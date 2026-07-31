-- COURSE-CATALOG-EXPANSION-01: Multi-subject expansion (Option A)
-- Expands catalog to 5 subjects per UML diagram: ENGLISH, MATH, SCIENCE, PROGRAMMING, HISTORY
-- All identifiers are stable UUIDv7 values. Seed is idempotent (ON CONFLICT DO NOTHING).

-- Expand English Foundations with additional topics
INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-0006-7000-8000-000000000001', '019f7e39-0000-7000-8000-000000000001', 'grammar-basics', 'Grammar Basics', 3),
  ('019f7e39-0007-7000-8000-000000000001', '019f7e39-0000-7000-8000-000000000001', 'vocabulary-building', 'Vocabulary Building', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0008-7000-8000-000000000001', '019f7e39-0006-7000-8000-000000000001', 'subject-verb-agreement', 'Subject-verb agreement', 'Learn how to match subjects and verbs correctly in simple sentences.', 12, 'beginner', 1),
  ('019f7e39-0009-7000-8000-000000000001', '019f7e39-0006-7000-8000-000000000001', 'past-tense-basics', 'Past tense basics', 'Understand and use simple past tense for regular verbs.', 15, 'beginner', 2),
  ('019f7e39-000a-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'common-greetings-vocabulary', 'Common greetings vocabulary', 'Expand your vocabulary with common greeting words and phrases.', 10, 'beginner', 1),
  ('019f7e39-000b-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'daily-routine-vocabulary', 'Daily routine vocabulary', 'Learn vocabulary for describing daily activities and routines.', 12, 'beginner', 2)
ON CONFLICT (id) DO NOTHING;

-- Add Math Foundations subject
INSERT INTO subjects (id, slug, name, display_order) VALUES
  ('019f7e39-000c-7000-8000-000000000001', 'math-foundations', 'Math Foundations', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-000d-7000-8000-000000000001', '019f7e39-000c-7000-8000-000000000001', 'numbers-and-counting', 'Numbers and Counting', 1),
  ('019f7e39-000e-7000-8000-000000000001', '019f7e39-000c-7000-8000-000000000001', 'basic-operations', 'Basic Operations', 2),
  ('019f7e39-000f-7000-8000-000000000001', '019f7e39-000c-7000-8000-000000000001', 'simple-equations', 'Simple Equations', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0010-7000-8000-000000000001', '019f7e39-000d-7000-8000-000000000001', 'whole-numbers', 'Whole numbers', 'Understand place value and read whole numbers up to thousands.', 15, 'beginner', 1),
  ('019f7e39-0011-7000-8000-000000000001', '019f7e39-000d-7000-8000-000000000001', 'counting-sequences', 'Counting sequences', 'Practice counting by 1s, 2s, 5s, and 10s.', 12, 'beginner', 2),
  ('019f7e39-0012-7000-8000-000000000001', '019f7e39-000e-7000-8000-000000000001', 'addition-basics', 'Addition basics', 'Learn addition facts and strategies for single-digit numbers.', 15, 'beginner', 1),
  ('019f7e39-0013-7000-8000-000000000001', '019f7e39-000e-7000-8000-000000000001', 'subtraction-basics', 'Subtraction basics', 'Understand subtraction as taking away and finding differences.', 15, 'beginner', 2),
  ('019f7e39-0014-7000-8000-000000000001', '019f7e39-000f-7000-8000-000000000001', 'solving-x', 'Solving for x', 'Solve simple one-step equations with one variable.', 18, 'beginner', 1),
  ('019f7e39-0015-7000-8000-000000000001', '019f7e39-000f-7000-8000-000000000001', 'word-problems', 'Simple word problems', 'Translate basic word problems into equations and solve them.', 20, 'beginner', 2)
ON CONFLICT (id) DO NOTHING;

-- Add Science Basics subject
INSERT INTO subjects (id, slug, name, display_order) VALUES
  ('019f7e39-0016-7000-8000-000000000001', 'science-basics', 'Science Basics', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-0017-7000-8000-000000000001', '019f7e39-0016-7000-8000-000000000001', 'scientific-method', 'Scientific Method', 1),
  ('019f7e39-0018-7000-8000-000000000001', '019f7e39-0016-7000-8000-000000000001', 'matter-and-energy', 'Matter and Energy', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0019-7000-8000-000000000001', '019f7e39-0017-7000-8000-000000000001', 'observation-and-hypothesis', 'Observation and hypothesis', 'Learn to make observations and form testable hypotheses.', 15, 'beginner', 1),
  ('019f7e39-001a-7000-8000-000000000001', '019f7e39-0017-7000-8000-000000000001', 'conducting-experiments', 'Conducting experiments', 'Understand the basics of designing and conducting simple experiments.', 18, 'beginner', 2),
  ('019f7e39-001b-7000-8000-000000000001', '019f7e39-0018-7000-8000-000000000001', 'states-of-matter', 'States of matter', 'Explore the three states of matter: solid, liquid, and gas.', 15, 'beginner', 1),
  ('019f7e39-001c-7000-8000-000000000001', '019f7e39-0018-7000-8000-000000000001', 'energy-forms', 'Forms of energy', 'Learn about different forms of energy including light, heat, and motion.', 18, 'beginner', 2)
ON CONFLICT (id) DO NOTHING;

-- Add Programming Fundamentals subject
INSERT INTO subjects (id, slug, name, display_order) VALUES
  ('019f7e39-001d-7000-8000-000000000001', 'programming-fundamentals', 'Programming Fundamentals', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-001e-7000-8000-000000000001', '019f7e39-001d-7000-8000-000000000001', 'variables-and-data-types', 'Variables and Data Types', 1),
  ('019f7e39-001f-7000-8000-000000000001', '019f7e39-001d-7000-8000-000000000001', 'control-structures', 'Control Structures', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0020-7000-8000-000000000001', '019f7e39-001e-7000-8000-000000000001', 'what-is-a-variable', 'What is a variable', 'Understand what variables are and how to store data in them.', 15, 'beginner', 1),
  ('019f7e39-0021-7000-8000-000000000001', '019f7e39-001e-7000-8000-000000000001', 'data-types', 'Data types', 'Learn about basic data types: numbers, strings, and booleans.', 18, 'beginner', 2),
  ('019f7e39-0022-7000-8000-000000000001', '019f7e39-001f-7000-8000-000000000001', 'if-statements', 'If statements', 'Use conditional logic to make decisions in programs.', 15, 'beginner', 1),
  ('019f7e39-0023-7000-8000-000000000001', '019f7e39-001f-7000-8000-000000000001', 'loops', 'Loops', 'Understand how to repeat actions using loops.', 18, 'beginner', 2)
ON CONFLICT (id) DO NOTHING;

-- Add History Overview subject
INSERT INTO subjects (id, slug, name, display_order) VALUES
  ('019f7e39-0024-7000-8000-000000000001', 'history-overview', 'History Overview', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-0025-7000-8000-000000000001', '019f7e39-0024-7000-8000-000000000001', 'ancient-civilizations', 'Ancient Civilizations', 1),
  ('019f7e39-0026-7000-8000-000000000001', '019f7e39-0024-7000-8000-000000000001', 'modern-history', 'Modern History', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0027-7000-8000-000000000001', '019f7e39-0025-7000-8000-000000000001', 'early-civilizations', 'Early civilizations', 'Explore the characteristics of early human civilizations.', 18, 'beginner', 1),
  ('019f7e39-0028-7000-8000-000000000001', '019f7e39-0025-7000-8000-000000000001', 'ancient-empires', 'Ancient empires', 'Learn about major ancient empires and their contributions.', 20, 'beginner', 2),
  ('019f7e39-0029-7000-8000-000000000001', '019f7e39-0026-7000-8000-000000000001', 'industrial-revolution', 'Industrial Revolution', 'Understand the impact of the Industrial Revolution on society.', 20, 'beginner', 1),
  ('019f7e39-002a-7000-8000-000000000001', '019f7e39-0026-7000-8000-000000000001', 'contemporary-world', 'Contemporary world', 'Explore major events and developments in the modern world.', 22, 'beginner', 2)
ON CONFLICT (id) DO NOTHING;
