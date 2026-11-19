# Digital Voting System (Java Swing + MySQL)

| Component | Technology | Where in the code |
|---|---|---|
| Frontend | Java Swing | `ui/` |
| Backend | Java | `service/`, `dao/` |
| Database | MySQL (+ MySQL Workbench) | `sql/schema.sql`, `db/Database.java` |
| Face recognition | OpenCV + JavaCV | `api/FaceService.java`, `ui/CameraPanel.java` |
| Email OTP | JavaMail (Jakarta Mail) | `api/EmailService.java` |
| Mobile OTP | SMS gateway HTTP API | `api/SmsService.java` |
| Aadhaar verification | UIDAI-authorised gateway | `api/AadhaarService.java` |

Screens (from the UI design): landing, login (password → OTP → face), 4-step registration, ballot, receipt, results / "not declared yet", admin login, admin dashboard (voters, candidates & parties, election scheduling, reports, roles & permissions).

---

## Step 1 – Install the tools
1. **JDK 17 or newer** (`java -version`) and **Apache Maven** (`mvn -v`).
2. **MySQL Server 8** and **MySQL Workbench**.
3. **VS Code** + the *Extension Pack for Java* (VS Code will offer it from `.vscode/extensions.json`).
4. **Git** (`git --version`) and a GitHub account.

## Step 2 – Create the database (MySQL Workbench)
1. Open Workbench → connect to your local MySQL instance.
2. `File > Open SQL Script…` → `sql/schema.sql` → click the ⚡ **Execute** button. This creates the `digital_voting` database and all tables.
3. (Optional) run `sql/sample_data.sql` for demo parties, an election and candidates.
4. (Recommended) uncomment the `CREATE USER` / `GRANT` lines at the top of `schema.sql` and use that `voting_app` user instead of `root`.
5. Refresh the *Schemas* panel to see the tables.

## Step 3 – Connect the app to MySQL
1. Copy `config.example.properties` to `config.properties` (git-ignored).
2. Fill in `db.user` and `db.password`. `Database.java` builds the JDBC URL from `db.host / db.port / db.name`; the driver (`mysql-connector-j`) comes from `pom.xml`.
3. The landing screen shows **"Database: connected"** when this works.

## Step 4 – Connect the APIs (all in `config.properties`)
**Email OTP (JavaMail).** Set `smtp.host`, `smtp.port`, `smtp.username`, `smtp.password`, `smtp.from`. For Gmail, enable 2-step verification and create an *App Password* (use it as `smtp.password`). Leave `smtp.host` empty to print OTPs to the console while developing.

**Mobile OTP (SMS gateway).** Set `sms.mode=http`, `sms.url`, `sms.api_key`, `sms.sender_id`. `SmsService` sends `POST {"to","sender","message"}` with a Bearer token; change `SmsService.sendOtp` to match your provider's request format (MSG91, Twilio, Fast2SMS…). India's DLT sender/template registration applies. With `sms.mode=console` OTPs are printed to the console.

**Aadhaar verification.** `aadhaar.mode=mock` validates the 12-digit format and Verhoeff checksum only (no identity check). `aadhaar.mode=live` calls `aadhaar.url` with `aadhaar.license_key`; real UIDAI authentication is only available through a licensed AUA/KUA or an authorised partner, so edit the request/response handling in `AadhaarService.verify` to that provider's contract. The app never stores the raw Aadhaar number: only a peppered SHA-256 hash and the last 4 digits.

**Face recognition (OpenCV + JavaCV).** `pom.xml` pulls `javacv-platform` (large download). Then download `haarcascade_frontalface_default.xml` from the OpenCV repository (`opencv/data/haarcascades/`) and place it in `src/main/resources/cascades/`. Registration stores 3+ face crops; login matches a fresh capture against them (`face.threshold`: lower = stricter).

## Step 5 – Build and run in VS Code
```bash
mvn compile
mvn exec:java                      # or press F5 in VS Code ("Digital Voting System")
```
Create the first admin (no default password is shipped):
```bash
mvn compile exec:java -Dexec.mainClass=com.digitalvoting.tools.CreateAdmin \
  -Dexec.args="admin 'Str0ng#Pass' 'Chief Admin' SUPER_ADMIN"
```
Typical test flow: register a voter → admin logs in → approve the voter → voter logs in (password, OTP, face) → votes → admin schedules/declares results → results screen.

## Step 6 – Version control with Git and GitHub
```bash
git init                                   # already done in this folder
git add .
git commit -m "Initial commit: Digital Voting System"
# create an EMPTY repo on github.com (no README), then:
git remote add origin https://github.com/<your-username>/digital-voting-system.git
git branch -M main
git push -u origin main
```
`config.properties` is in `.gitignore`, so passwords and API keys are never pushed. Daily flow: `git checkout -b feature/x` → commit → push → open a Pull Request.

## How voting integrity works
* `voter_participation` has a primary key `(election_id, voter_id)`: the database itself rejects a second vote.
* `votes` has **no voter column**, so ballots are anonymous; the voter only gets a random receipt code.
* Vote + participation are written in one transaction.
* Passwords use PBKDF2; OTPs are stored hashed, expire, and lock after 5 wrong tries.
* Every admin action and login is written to `audit_log`.

## Known limits (read before real use)
Face matching here is a basic LBPH model without liveness detection (a photo can fool it). A real public election needs far stronger controls (liveness checks, TLS-protected server-side backend instead of a desktop app talking straight to the database, independent audit). Treat this as a learning/project implementation.
