# Study Mentor — Full Product v1

Đây là gói dự án tự chứa dùng để chạy và trình bày sản phẩm môn học.

## Cấu trúc

- `app/`: ứng dụng Android.
- `backend/`: Node.js API, PostgreSQL migrations và kiểm thử backend.
- `contracts/`: hợp đồng OpenAPI 3.1 dùng chung.
- `docs/`: tài liệu nghiệm thu và báo cáo triển khai.

Hai thư mục `backend/` và `contracts/` là bản sao độc lập nằm trong chính dự án này. Không cần truy cập `EL_English_Web` hoặc thư mục `contracts` bên ngoài để build và kiểm thử.

## Chuẩn bị backend lần đầu trên Windows

Yêu cầu: Node.js 20+, Docker Desktop và JDK 17.

```powershell
cd backend
Copy-Item .env.example .env
npm ci
npm run db:up
npm run db:migrate:dev
npm start
```

API chạy tại `http://127.0.0.1:8080/api/v1` và health check tại `http://127.0.0.1:8080/health`.

PostgreSQL chỉ mở cục bộ tại `127.0.0.1:54329`. Tệp `.env` và `node_modules` không được đưa vào Git.

## Chạy ứng dụng Android

Giữ backend đang chạy, sau đó mở một terminal khác tại thư mục gốc:

```powershell
$env:GRADLE_USER_HOME="D:/gradle-cache"
.\gradlew.bat assembleDebug --no-daemon
```

Cài `app/build/outputs/apk/debug/app-debug.apk` vào Android Emulator. Bản debug dùng `10.0.2.2:8080`, là địa chỉ máy tính chủ từ Android Emulator.

## Kiểm tra chất lượng

Backend và OpenAPI:

```powershell
cd backend
npm run verify
```

Kiểm thử đầy đủ gồm PostgreSQL:

```powershell
cd backend
npm run verify:full
```

Android:

```powershell
$env:GRADLE_USER_HOME="D:/gradle-cache"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease --no-daemon
```

## Phạm vi Full Product v1

Phiên bản học thuật bao gồm xác thực, khóa học, quiz, ôn đáp án sai, flashcards, AI tutor giả lập an toàn, tiến độ, streak recovery, campaign, boss challenge, ví, cửa hàng và inventory. Phát hành Google Play, máy chủ production, push notification từ xa, speaking provider và leaderboard công khai không thuộc phạm vi nộp môn học.
