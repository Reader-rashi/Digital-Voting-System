-- =====================================================================
-- Digital Voting System - MySQL schema
-- Run in MySQL Workbench: File > Open SQL Script > schema.sql > Execute (lightning icon)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS digital_voting
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE digital_voting;

-- Dedicated application user (change the password, then put it in config.properties)
-- CREATE USER IF NOT EXISTS 'voting_app'@'localhost' IDENTIFIED BY 'ChangeMe#123';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON digital_voting.* TO 'voting_app'@'localhost';
-- FLUSH PRIVILEGES;

-- ---------- Admin accounts (roles: SUPER_ADMIN, ELECTION_OFFICER, AUDITOR) ----------
CREATE TABLE IF NOT EXISTS admins (
  admin_id      INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  full_name     VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('SUPER_ADMIN','ELECTION_OFFICER','AUDITOR') NOT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- Voters ----------
-- The raw Aadhaar number is NEVER stored: only a salted hash (for duplicate detection) and last 4 digits.
CREATE TABLE IF NOT EXISTS voters (
  voter_id      INT AUTO_INCREMENT PRIMARY KEY,
  full_name     VARCHAR(100) NOT NULL,
  dob           DATE         NOT NULL,
  gender        VARCHAR(15)  NOT NULL,
  aadhaar_hash  CHAR(64)     NOT NULL UNIQUE,
  aadhaar_last4 CHAR(4)      NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  mobile        VARCHAR(15)  NOT NULL UNIQUE,
  constituency  VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  face_enrolled TINYINT(1)   NOT NULL DEFAULT 0,
  status        ENUM('PENDING','APPROVED','REJECTED','BLOCKED') NOT NULL DEFAULT 'PENDING',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Face templates (grayscale 100x100 PNG crops used by OpenCV LBPH)
CREATE TABLE IF NOT EXISTS face_templates (
  template_id INT AUTO_INCREMENT PRIMARY KEY,
  voter_id    INT NOT NULL,
  image       MEDIUMBLOB NOT NULL,
  FOREIGN KEY (voter_id) REFERENCES voters(voter_id) ON DELETE CASCADE
);

-- ---------- Elections, parties, candidates ----------
CREATE TABLE IF NOT EXISTS elections (
  election_id      INT AUTO_INCREMENT PRIMARY KEY,
  title            VARCHAR(150) NOT NULL,
  start_time       DATETIME NOT NULL,
  end_time         DATETIME NOT NULL,
  results_declared TINYINT(1) NOT NULL DEFAULT 0,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS parties (
  party_id INT AUTO_INCREMENT PRIMARY KEY,
  name     VARCHAR(100) NOT NULL UNIQUE,
  symbol   VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS candidates (
  candidate_id INT AUTO_INCREMENT PRIMARY KEY,
  election_id  INT NOT NULL,
  party_id     INT NULL,
  name         VARCHAR(100) NOT NULL,
  FOREIGN KEY (election_id) REFERENCES elections(election_id) ON DELETE CASCADE,
  FOREIGN KEY (party_id)    REFERENCES parties(party_id) ON DELETE SET NULL
);

-- ---------- Ballot secrecy ----------
-- votes has NO voter reference. voter_participation records only THAT a voter voted.
CREATE TABLE IF NOT EXISTS votes (
  vote_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  election_id  INT NOT NULL,
  candidate_id INT NOT NULL,
  receipt_code CHAR(16) NOT NULL UNIQUE,
  cast_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (election_id)  REFERENCES elections(election_id),
  FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id)
);

CREATE TABLE IF NOT EXISTS voter_participation (
  election_id INT NOT NULL,
  voter_id    INT NOT NULL,
  voted_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (election_id, voter_id),          -- one vote per voter per election
  FOREIGN KEY (election_id) REFERENCES elections(election_id),
  FOREIGN KEY (voter_id)    REFERENCES voters(voter_id)
);

-- ---------- OTP + audit ----------
CREATE TABLE IF NOT EXISTS otp_requests (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  target      VARCHAR(150) NOT NULL,            -- email address or mobile number
  purpose     VARCHAR(30)  NOT NULL,            -- REG_EMAIL, REG_MOBILE, LOGIN
  otp_hash    CHAR(64)     NOT NULL,
  expires_at  DATETIME     NOT NULL,
  attempts    INT          NOT NULL DEFAULT 0,
  used        TINYINT(1)   NOT NULL DEFAULT 0,
  verified_at DATETIME     NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_otp_lookup (target, purpose)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor     VARCHAR(100) NOT NULL,
  action    VARCHAR(60)  NOT NULL,
  detail    VARCHAR(500),
  logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
