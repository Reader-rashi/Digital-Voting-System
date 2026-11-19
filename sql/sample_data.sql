-- Optional demo data (run after schema.sql). Admin accounts are created with the CreateAdmin tool (see README).
USE digital_voting;

INSERT IGNORE INTO parties (name, symbol) VALUES
  ('Party A', 'Lotus'),
  ('Party B', 'Wheel'),
  ('Independent', 'Kite');

INSERT INTO elections (title, start_time, end_time)
VALUES ('General Election 2026 (Demo)', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY));

INSERT INTO candidates (election_id, party_id, name)
SELECT e.election_id, p.party_id, c.name
FROM elections e
JOIN (SELECT 'Candidate One' AS name, 'Party A' AS party
      UNION ALL SELECT 'Candidate Two', 'Party B'
      UNION ALL SELECT 'Candidate Three', 'Independent') c
JOIN parties p ON p.name = c.party
WHERE e.title = 'General Election 2026 (Demo)';
