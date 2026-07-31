-- COURSE-QUIZ-CONTENT-EXPANSION-01
-- Backend-authoritative, idempotent quiz content for the approved five-subject catalog.
-- 13 active topics x 30 questions; every reachable question has four options.

DO $$
DECLARE
  active_subjects integer;
  active_topics integer;
  active_lessons integer;
BEGIN
  SELECT count(*) INTO active_subjects FROM subjects WHERE active;
  SELECT count(*) INTO active_topics FROM topics t JOIN subjects s ON s.id = t.subject_id WHERE t.active AND s.active;
  SELECT count(*) INTO active_lessons FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE l.active AND t.active AND s.active;
  IF active_subjects <> 5 OR active_topics <> 13 OR active_lessons <> 25 THEN
    RAISE EXCEPTION 'COURSE-QUIZ-CONTENT-EXPANSION-01 requires the approved 5/13/25 catalog; found %/%/%', active_subjects, active_topics, active_lessons;
  END IF;
END $$;

-- Exactly one active quiz per active lesson. Existing active content for the
-- hello-and-goodbye lesson is reused; the inactive introduction fixture stays inactive.
WITH active_lessons AS (
  SELECT l.id, l.slug, l.title, row_number() OVER (ORDER BY s.display_order, t.display_order, l.display_order, l.id) AS seed_no
  FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id
  WHERE s.active AND t.active AND l.active
), expected AS (
  SELECT id AS lesson_id, slug || '-check' AS slug, title || ' check' AS title,
    'Check understanding of ' || lower(title) || '.' AS description, seed_no
  FROM active_lessons
)
UPDATE quizzes q SET active = FALSE
WHERE q.active
  AND q.lesson_id IN (SELECT lesson_id FROM expected)
  AND NOT EXISTS (SELECT 1 FROM expected e WHERE e.lesson_id = q.lesson_id AND e.slug = q.slug);

WITH active_lessons AS (
  SELECT l.id, l.slug, l.title, row_number() OVER (ORDER BY s.display_order, t.display_order, l.display_order, l.id) AS seed_no
  FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id
  WHERE s.active AND t.active AND l.active
)
INSERT INTO quizzes (id, lesson_id, slug, title, description, display_order, active)
SELECT
  CASE WHEN slug = 'hello-and-goodbye' THEN '019f7e39-0006-7000-8000-000000000001'::uuid
       ELSE format('019f7e3a-%s-7000-8000-000000000001', lpad(to_hex(seed_no), 4, '0'))::uuid END,
  id, slug || '-check', title || ' check', 'Check understanding of ' || lower(title) || '.', 1, TRUE
FROM active_lessons
ON CONFLICT (lesson_id, slug) DO UPDATE
SET title = EXCLUDED.title, description = EXCLUDED.description, display_order = EXCLUDED.display_order, active = TRUE;

-- The legacy second greeting question is reassigned before new display-order
-- rows are inserted, freeing order 2 in the hello-and-goodbye quiz.
UPDATE quiz_questions
SET quiz_id = (SELECT q.id FROM quizzes q JOIN lessons l ON l.id = q.lesson_id WHERE l.slug = 'introducing-yourself' AND q.slug = 'introducing-yourself-check' AND q.active),
    display_order = 1
WHERE id = '019f7e39-0008-7000-8000-000000000001';

-- Eight authored facts per topic produce the required ordered progression:
-- 1-8 recognition, 9-16 direct understanding, 17-24 application, 25-30 reasoning.
WITH topic_facts(topic_slug, fact_no, term, meaning) AS (
  VALUES
    ('greetings',1,'Good morning','a greeting used before noon'),
    ('greetings',2,'Goodbye','a phrase used when leaving'),
    ('greetings',3,'Hello','a general greeting when meeting someone'),
    ('greetings',4,'Good afternoon','a greeting used after noon'),
    ('greetings',5,'Good evening','a greeting used in the evening'),
    ('greetings',6,'Good night','a phrase used before sleep or at bedtime'),
    ('greetings',7,'See you later','a friendly farewell that suggests another meeting'),
    ('greetings',8,'How are you?','a question that politely asks about someone''s wellbeing'),
    ('daily-routines',1,'morning routine','a regular set of actions done in the morning'),
    ('daily-routines',2,'present tense','a verb form used for usual actions and routines'),
    ('daily-routines',3,'wake up','to stop sleeping'),
    ('daily-routines',4,'brush teeth','to clean teeth with a toothbrush'),
    ('daily-routines',5,'eat breakfast','to have the first meal of the day'),
    ('daily-routines',6,'go to school or work','to travel to a regular daytime place'),
    ('daily-routines',7,'sequence words','words such as first, next, and finally that show order'),
    ('daily-routines',8,'daily activity','an action that someone usually does each day'),
    ('grammar-basics',1,'subject','the person, place, thing, or idea a sentence is about'),
    ('grammar-basics',2,'verb','a word that shows an action or state'),
    ('grammar-basics',3,'singular subject','one person, place, thing, or idea'),
    ('grammar-basics',4,'plural subject','more than one person, place, thing, or idea'),
    ('grammar-basics',5,'he, she, or it','a third-person singular subject'),
    ('grammar-basics',6,'I, you, we, or they','a subject that does not take a present-tense s ending'),
    ('grammar-basics',7,'present-tense singular verb','a verb that usually ends in s with he, she, or it'),
    ('grammar-basics',8,'regular past tense','a past-tense form usually made by adding ed'),
    ('vocabulary-building',1,'greeting','a word or phrase used when meeting someone'),
    ('vocabulary-building',2,'farewell','a word or phrase used when leaving someone'),
    ('vocabulary-building',3,'routine','a set of actions done regularly'),
    ('vocabulary-building',4,'activity','an action that someone does'),
    ('vocabulary-building',5,'morning','the early part of the day'),
    ('vocabulary-building',6,'evening','the later part of the day before night'),
    ('vocabulary-building',7,'practice','repeated work to improve a skill'),
    ('vocabulary-building',8,'context','the words or situation that help explain meaning'),
    ('numbers-and-counting',1,'digit','one symbol from 0 through 9 used to write numbers'),
    ('numbers-and-counting',2,'place value','the value a digit has because of its position'),
    ('numbers-and-counting',3,'ones place','the position that represents single units'),
    ('numbers-and-counting',4,'tens place','the position that represents groups of ten'),
    ('numbers-and-counting',5,'hundreds place','the position that represents groups of one hundred'),
    ('numbers-and-counting',6,'thousand','a quantity equal to ten hundreds'),
    ('numbers-and-counting',7,'number line','a line that shows numbers in order'),
    ('numbers-and-counting',8,'counting sequence','numbers listed in a consistent order or pattern'),
    ('basic-operations',1,'addend','a number that is added to another number'),
    ('basic-operations',2,'sum','the result of an addition'),
    ('basic-operations',3,'minuend','the number from which another number is subtracted'),
    ('basic-operations',4,'subtrahend','the number being subtracted'),
    ('basic-operations',5,'difference','the result of a subtraction'),
    ('basic-operations',6,'addition','an operation that combines quantities'),
    ('basic-operations',7,'subtraction','an operation that finds how much remains or the distance between quantities'),
    ('basic-operations',8,'inverse operations','operations that undo each other, such as addition and subtraction'),
    ('simple-equations',1,'variable','a symbol that represents an unknown number'),
    ('simple-equations',2,'equation','a statement that says two expressions have the same value'),
    ('simple-equations',3,'inverse operation','an operation used to undo another operation'),
    ('simple-equations',4,'addition property of equality','adding the same number to both sides keeps an equation balanced'),
    ('simple-equations',5,'subtraction property of equality','subtracting the same number from both sides keeps an equation balanced'),
    ('simple-equations',6,'solution','a value that makes an equation true'),
    ('simple-equations',7,'balance','the idea that both sides of an equation must stay equal'),
    ('simple-equations',8,'check','substituting a value back into an equation to test it'),
    ('scientific-method',1,'observation','information noticed with senses or tools'),
    ('scientific-method',2,'hypothesis','a testable possible explanation or prediction'),
    ('scientific-method',3,'experiment','a planned test used to investigate a question'),
    ('scientific-method',4,'variable','a factor that can change in an experiment'),
    ('scientific-method',5,'independent variable','the factor a researcher deliberately changes'),
    ('scientific-method',6,'dependent variable','the result that is measured or observed'),
    ('scientific-method',7,'data','recorded observations or measurements'),
    ('scientific-method',8,'conclusion','a statement that explains what the results show'),
    ('matter-and-energy',1,'matter','anything that has mass and takes up space'),
    ('matter-and-energy',2,'solid','matter with a definite shape and definite volume'),
    ('matter-and-energy',3,'liquid','matter with a definite volume but no definite shape'),
    ('matter-and-energy',4,'gas','matter with no definite shape or volume'),
    ('matter-and-energy',5,'energy','the ability to cause change or do work'),
    ('matter-and-energy',6,'light energy','energy carried by visible light'),
    ('matter-and-energy',7,'heat energy','energy related to the motion of particles and temperature'),
    ('matter-and-energy',8,'motion energy','energy an object has because it is moving'),
    ('variables-and-data-types',1,'variable','a named place used to store a value in a program'),
    ('variables-and-data-types',2,'value','the data currently stored or used by a program'),
    ('variables-and-data-types',3,'number','a data type used for numerical values'),
    ('variables-and-data-types',4,'string','a data type used for text'),
    ('variables-and-data-types',5,'boolean','a data type with only true or false values'),
    ('variables-and-data-types',6,'assignment','putting a value into a variable'),
    ('variables-and-data-types',7,'data type','a category that tells a program how to treat data'),
    ('variables-and-data-types',8,'expression','code that calculates or produces a value'),
    ('control-structures',1,'condition','an expression that evaluates to true or false'),
    ('control-structures',2,'if statement','code that runs when a condition is true'),
    ('control-structures',3,'else branch','code that runs when an if condition is false'),
    ('control-structures',4,'loop','a control structure that repeats instructions'),
    ('control-structures',5,'iteration','one repetition of a loop'),
    ('control-structures',6,'true','the boolean value that represents a satisfied condition'),
    ('control-structures',7,'false','the boolean value that represents an unsatisfied condition'),
    ('control-structures',8,'sequence','instructions performed in their written order'),
    ('ancient-civilizations',1,'civilization','a complex society with organized communities and shared institutions'),
    ('ancient-civilizations',2,'agriculture','growing crops and raising animals for food'),
    ('ancient-civilizations',3,'settlement','a place where people establish a community'),
    ('ancient-civilizations',4,'writing','a system of symbols used to record language or information'),
    ('ancient-civilizations',5,'trade','the exchange of goods or services'),
    ('ancient-civilizations',6,'government','a system for making and enforcing community decisions'),
    ('ancient-civilizations',7,'artifact','an object made or used by people in the past'),
    ('ancient-civilizations',8,'empire','a group of territories governed by one central authority'),
    ('modern-history',1,'Industrial Revolution','a period when machine production and factories expanded greatly'),
    ('modern-history',2,'factory','a workplace where goods are made using organized labor and machinery'),
    ('modern-history',3,'urbanization','the growth of towns and cities as more people move there'),
    ('modern-history',4,'steam power','power produced by using steam to drive machines'),
    ('modern-history',5,'labor movement','organized efforts by workers to improve pay and working conditions'),
    ('modern-history',6,'technology','tools and methods created to solve practical problems'),
    ('modern-history',7,'globalization','increasing connections among people, economies, and cultures worldwide'),
    ('modern-history',8,'contemporary history','the study of relatively recent events and their effects')
), topic_slots AS (
  SELECT t.id AS topic_id, t.slug AS topic_slug,
    array_agg(l.id ORDER BY l.display_order, l.id) AS lesson_ids,
    count(*)::integer AS lesson_count
  FROM topics t JOIN subjects s ON s.id = t.subject_id JOIN lessons l ON l.topic_id = t.id
  WHERE s.active AND t.active AND l.active GROUP BY t.id, t.slug
), question_numbers AS (
  SELECT generate_series(1, 30) AS topic_order
), specs AS (
  SELECT ts.topic_id, ts.topic_slug, n.topic_order, f.fact_no, f.term, f.meaning,
    ts.lesson_ids[1 + ((n.topic_order - 1) % ts.lesson_count)] AS lesson_id,
    ((n.topic_order - 1) / ts.lesson_count) + 1 AS lesson_order,
    row_number() OVER (ORDER BY ts.topic_slug, n.topic_order) AS seed_no,
    CASE WHEN n.topic_order <= 8 THEN 'What is the best description of ' || f.term || '?'
         WHEN n.topic_order <= 16 THEN 'Which term best matches this description: ' || f.meaning || '?'
         WHEN n.topic_order <= 24 THEN 'A learner needs to work with ' || f.meaning || '. Which concept is most relevant?'
         ELSE 'Which statement best explains why ' || f.term || ' matters?' END AS prompt,
    CASE WHEN n.topic_order <= 8 THEN f.meaning
         WHEN n.topic_order <= 24 THEN f.term
         ELSE 'It involves ' || f.meaning || '.' END AS correct_text,
    CASE WHEN n.topic_order <= 8 THEN 'meaning'
         WHEN n.topic_order <= 24 THEN 'term'
         ELSE 'reason' END AS answer_kind
  FROM topic_slots ts CROSS JOIN question_numbers n
  JOIN topic_facts f ON f.topic_slug = ts.topic_slug
    AND f.fact_no = CASE WHEN n.topic_order <= 24 THEN ((n.topic_order - 1) % 8) + 1 ELSE n.topic_order - 24 END
), quiz_specs AS (
  SELECT sp.*, q.id AS quiz_id
  FROM specs sp JOIN lessons l ON l.id = sp.lesson_id JOIN quizzes q ON q.lesson_id = l.id AND q.slug = l.slug || '-check' AND q.active
)
INSERT INTO quiz_questions (id, quiz_id, prompt, question_type, display_order)
SELECT
  CASE WHEN topic_slug = 'greetings' AND topic_order = 1 THEN '019f7e39-0007-7000-8000-000000000001'::uuid
       WHEN topic_slug = 'greetings' AND topic_order = 2 THEN '019f7e39-0008-7000-8000-000000000001'::uuid
       ELSE format('019f7e3a-%s-7000-8000-000000000001', lpad(to_hex(768 + seed_no), 4, '0'))::uuid END,
  quiz_id,
  CASE WHEN topic_slug = 'greetings' AND topic_order = 1 THEN 'Which greeting is appropriate in the morning?'
       WHEN topic_slug = 'greetings' AND topic_order = 2 THEN 'Which phrase is a farewell?'
       ELSE prompt END,
  'single-choice', lesson_order
FROM quiz_specs
ON CONFLICT (id) DO UPDATE SET quiz_id = EXCLUDED.quiz_id, prompt = EXCLUDED.prompt, question_type = EXCLUDED.question_type, display_order = EXCLUDED.display_order;

-- Complete the two pre-existing active questions to four options. The inactive
-- development fixture and its one option are deliberately not changed.
INSERT INTO quiz_answer_options (id, question_id, text, display_order, correct) VALUES
  ('019f7e3a-00f0-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'Good afternoon', 4, FALSE),
  ('019f7e3a-00f1-7000-8000-000000000001', '019f7e39-0008-7000-8000-000000000001', 'Good morning', 4, FALSE)
ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, display_order = EXCLUDED.display_order, correct = EXCLUDED.correct;

WITH topic_facts(topic_slug, fact_no, term, meaning) AS (
  SELECT topic_slug, fact_no, term, meaning FROM (VALUES
    ('greetings',1,'Good morning','a greeting used before noon'),('greetings',2,'Goodbye','a phrase used when leaving'),('greetings',3,'Hello','a general greeting when meeting someone'),('greetings',4,'Good afternoon','a greeting used after noon'),('greetings',5,'Good evening','a greeting used in the evening'),('greetings',6,'Good night','a phrase used before sleep or at bedtime'),('greetings',7,'See you later','a friendly farewell that suggests another meeting'),('greetings',8,'How are you?','a question that politely asks about someone''s wellbeing'),
    ('daily-routines',1,'morning routine','a regular set of actions done in the morning'),('daily-routines',2,'present tense','a verb form used for usual actions and routines'),('daily-routines',3,'wake up','to stop sleeping'),('daily-routines',4,'brush teeth','to clean teeth with a toothbrush'),('daily-routines',5,'eat breakfast','to have the first meal of the day'),('daily-routines',6,'go to school or work','to travel to a regular daytime place'),('daily-routines',7,'sequence words','words such as first, next, and finally that show order'),('daily-routines',8,'daily activity','an action that someone usually does each day'),
    ('grammar-basics',1,'subject','the person, place, thing, or idea a sentence is about'),('grammar-basics',2,'verb','a word that shows an action or state'),('grammar-basics',3,'singular subject','one person, place, thing, or idea'),('grammar-basics',4,'plural subject','more than one person, place, thing, or idea'),('grammar-basics',5,'he, she, or it','a third-person singular subject'),('grammar-basics',6,'I, you, we, or they','a subject that does not take a present-tense s ending'),('grammar-basics',7,'present-tense singular verb','a verb that usually ends in s with he, she, or it'),('grammar-basics',8,'regular past tense','a past-tense form usually made by adding ed'),
    ('vocabulary-building',1,'greeting','a word or phrase used when meeting someone'),('vocabulary-building',2,'farewell','a word or phrase used when leaving someone'),('vocabulary-building',3,'routine','a set of actions done regularly'),('vocabulary-building',4,'activity','an action that someone does'),('vocabulary-building',5,'morning','the early part of the day'),('vocabulary-building',6,'evening','the later part of the day before night'),('vocabulary-building',7,'practice','repeated work to improve a skill'),('vocabulary-building',8,'context','the words or situation that help explain meaning'),
    ('numbers-and-counting',1,'digit','one symbol from 0 through 9 used to write numbers'),('numbers-and-counting',2,'place value','the value a digit has because of its position'),('numbers-and-counting',3,'ones place','the position that represents single units'),('numbers-and-counting',4,'tens place','the position that represents groups of ten'),('numbers-and-counting',5,'hundreds place','the position that represents groups of one hundred'),('numbers-and-counting',6,'thousand','a quantity equal to ten hundreds'),('numbers-and-counting',7,'number line','a line that shows numbers in order'),('numbers-and-counting',8,'counting sequence','numbers listed in a consistent order or pattern'),
    ('basic-operations',1,'addend','a number that is added to another number'),('basic-operations',2,'sum','the result of an addition'),('basic-operations',3,'minuend','the number from which another number is subtracted'),('basic-operations',4,'subtrahend','the number being subtracted'),('basic-operations',5,'difference','the result of a subtraction'),('basic-operations',6,'addition','an operation that combines quantities'),('basic-operations',7,'subtraction','an operation that finds how much remains or the distance between quantities'),('basic-operations',8,'inverse operations','operations that undo each other, such as addition and subtraction'),
    ('simple-equations',1,'variable','a symbol that represents an unknown number'),('simple-equations',2,'equation','a statement that says two expressions have the same value'),('simple-equations',3,'inverse operation','an operation used to undo another operation'),('simple-equations',4,'addition property of equality','adding the same number to both sides keeps an equation balanced'),('simple-equations',5,'subtraction property of equality','subtracting the same number from both sides keeps an equation balanced'),('simple-equations',6,'solution','a value that makes an equation true'),('simple-equations',7,'balance','the idea that both sides of an equation must stay equal'),('simple-equations',8,'check','substituting a value back into an equation to test it'),
    ('scientific-method',1,'observation','information noticed with senses or tools'),('scientific-method',2,'hypothesis','a testable possible explanation or prediction'),('scientific-method',3,'experiment','a planned test used to investigate a question'),('scientific-method',4,'variable','a factor that can change in an experiment'),('scientific-method',5,'independent variable','the factor a researcher deliberately changes'),('scientific-method',6,'dependent variable','the result that is measured or observed'),('scientific-method',7,'data','recorded observations or measurements'),('scientific-method',8,'conclusion','a statement that explains what the results show'),
    ('matter-and-energy',1,'matter','anything that has mass and takes up space'),('matter-and-energy',2,'solid','matter with a definite shape and definite volume'),('matter-and-energy',3,'liquid','matter with a definite volume but no definite shape'),('matter-and-energy',4,'gas','matter with no definite shape or volume'),('matter-and-energy',5,'energy','the ability to cause change or do work'),('matter-and-energy',6,'light energy','energy carried by visible light'),('matter-and-energy',7,'heat energy','energy related to the motion of particles and temperature'),('matter-and-energy',8,'motion energy','energy an object has because it is moving'),
    ('variables-and-data-types',1,'variable','a named place used to store a value in a program'),('variables-and-data-types',2,'value','the data currently stored or used by a program'),('variables-and-data-types',3,'number','a data type used for numerical values'),('variables-and-data-types',4,'string','a data type used for text'),('variables-and-data-types',5,'boolean','a data type with only true or false values'),('variables-and-data-types',6,'assignment','putting a value into a variable'),('variables-and-data-types',7,'data type','a category that tells a program how to treat data'),('variables-and-data-types',8,'expression','code that calculates or produces a value'),
    ('control-structures',1,'condition','an expression that evaluates to true or false'),('control-structures',2,'if statement','code that runs when a condition is true'),('control-structures',3,'else branch','code that runs when an if condition is false'),('control-structures',4,'loop','a control structure that repeats instructions'),('control-structures',5,'iteration','one repetition of a loop'),('control-structures',6,'true','the boolean value that represents a satisfied condition'),('control-structures',7,'false','the boolean value that represents an unsatisfied condition'),('control-structures',8,'sequence','instructions performed in their written order'),
    ('ancient-civilizations',1,'civilization','a complex society with organized communities and shared institutions'),('ancient-civilizations',2,'agriculture','growing crops and raising animals for food'),('ancient-civilizations',3,'settlement','a place where people establish a community'),('ancient-civilizations',4,'writing','a system of symbols used to record language or information'),('ancient-civilizations',5,'trade','the exchange of goods or services'),('ancient-civilizations',6,'government','a system for making and enforcing community decisions'),('ancient-civilizations',7,'artifact','an object made or used by people in the past'),('ancient-civilizations',8,'empire','a group of territories governed by one central authority'),
    ('modern-history',1,'Industrial Revolution','a period when machine production and factories expanded greatly'),('modern-history',2,'factory','a workplace where goods are made using organized labor and machinery'),('modern-history',3,'urbanization','the growth of towns and cities as more people move there'),('modern-history',4,'steam power','power produced by using steam to drive machines'),('modern-history',5,'labor movement','organized efforts by workers to improve pay and working conditions'),('modern-history',6,'technology','tools and methods created to solve practical problems'),('modern-history',7,'globalization','increasing connections among people, economies, and cultures worldwide'),('modern-history',8,'contemporary history','the study of relatively recent events and their effects')
  ) AS valueset(topic_slug, fact_no, term, meaning)
), topic_slots AS (
  SELECT t.slug AS topic_slug, array_agg(l.id ORDER BY l.display_order, l.id) AS lesson_ids, count(*)::integer AS lesson_count
  FROM topics t JOIN subjects s ON s.id=t.subject_id JOIN lessons l ON l.topic_id=t.id WHERE s.active AND t.active AND l.active GROUP BY t.slug
), specs AS (
  SELECT ts.topic_slug, n AS topic_order, f.fact_no, f.term, f.meaning,
    q.id AS quiz_id, row_number() OVER (ORDER BY ts.topic_slug, n) AS seed_no,
    CASE WHEN n <= 8 THEN 'meaning' WHEN n <= 24 THEN 'term' ELSE 'reason' END AS answer_kind
  FROM topic_slots ts CROSS JOIN generate_series(1,30) n
  JOIN topic_facts f ON f.topic_slug=ts.topic_slug AND f.fact_no=CASE WHEN n<=24 THEN ((n-1)%8)+1 ELSE n-24 END
  JOIN lessons l ON l.id=ts.lesson_ids[1+((n-1)%ts.lesson_count)]
  JOIN quizzes q ON q.lesson_id=l.id AND q.slug=l.slug || '-check' AND q.active
), choices AS (
  SELECT sp.*, c.choice_no, f.term AS choice_term, f.meaning AS choice_meaning
  FROM specs sp CROSS JOIN generate_series(0,3) c(choice_no)
  JOIN topic_facts f ON f.topic_slug=sp.topic_slug AND f.fact_no=((sp.fact_no-1+c.choice_no)%8)+1
)
INSERT INTO quiz_answer_options (id, question_id, text, display_order, correct)
SELECT
  format('019f7e3b-%s-7000-8000-000000000001', lpad(to_hex(4096 + ((seed_no - 1) * 4) + choice_no + 1), 4, '0'))::uuid,
  CASE WHEN topic_slug='greetings' AND topic_order=1 THEN '019f7e39-0007-7000-8000-000000000001'::uuid
       WHEN topic_slug='greetings' AND topic_order=2 THEN '019f7e39-0008-7000-8000-000000000001'::uuid
       ELSE format('019f7e3a-%s-7000-8000-000000000001', lpad(to_hex(768 + seed_no), 4, '0'))::uuid END,
  CASE WHEN answer_kind='meaning' THEN choice_meaning WHEN answer_kind='term' THEN choice_term ELSE 'It involves ' || choice_meaning || '.' END,
  choice_no + 1, choice_no = 0
FROM choices
WHERE NOT (topic_slug='greetings' AND topic_order IN (1,2))
ON CONFLICT (id) DO UPDATE SET text=EXCLUDED.text, display_order=EXCLUDED.display_order, correct=EXCLUDED.correct;

-- Deterministic postcondition checks make a partial or malformed seed fail fast.
DO $$
DECLARE
  question_total integer;
  option_total integer;
  bad_topics integer;
  bad_questions integer;
  bad_lessons integer;
BEGIN
  SELECT count(*) INTO question_total FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id JOIN lessons l ON l.id=q.lesson_id JOIN topics t ON t.id=l.topic_id JOIN subjects s ON s.id=t.subject_id WHERE q.active AND l.active AND t.active AND s.active;
  SELECT count(*) INTO option_total FROM quiz_answer_options o JOIN quiz_questions qq ON qq.id=o.question_id JOIN quizzes q ON q.id=qq.quiz_id JOIN lessons l ON l.id=q.lesson_id JOIN topics t ON t.id=l.topic_id JOIN subjects s ON s.id=t.subject_id WHERE q.active AND l.active AND t.active AND s.active;
  SELECT count(*) INTO bad_topics FROM (SELECT t.id FROM topics t JOIN subjects s ON s.id=t.subject_id JOIN lessons l ON l.topic_id=t.id JOIN quizzes q ON q.lesson_id=l.id AND q.active JOIN quiz_questions qq ON qq.quiz_id=q.id WHERE s.active AND t.active AND l.active GROUP BY t.id HAVING count(*)<>30) x;
  SELECT count(*) INTO bad_questions FROM (SELECT qq.id FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id JOIN lessons l ON l.id=q.lesson_id JOIN topics t ON t.id=l.topic_id JOIN subjects s ON s.id=t.subject_id JOIN quiz_answer_options o ON o.question_id=qq.id WHERE q.active AND l.active AND t.active AND s.active GROUP BY qq.id HAVING count(*)<>4 OR count(*) FILTER (WHERE o.correct)<>1) x;
  SELECT count(*) INTO bad_lessons FROM (SELECT l.id FROM lessons l JOIN topics t ON t.id=l.topic_id JOIN subjects s ON s.id=t.subject_id LEFT JOIN quizzes q ON q.lesson_id=l.id AND q.active WHERE l.active AND t.active AND s.active GROUP BY l.id HAVING count(q.id)<>1) x;
  IF question_total<>390 OR option_total<>1560 OR bad_topics<>0 OR bad_questions<>0 OR bad_lessons<>0 THEN
    RAISE EXCEPTION 'COURSE-QUIZ-CONTENT-EXPANSION-01 postcondition failed: questions %, options %, bad topics %, bad questions %, bad lessons %', question_total, option_total, bad_topics, bad_questions, bad_lessons;
  END IF;
END $$;
