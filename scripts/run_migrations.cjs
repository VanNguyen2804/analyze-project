const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

const DATABASE_URL = process.env.DATABASE_URL;
if (!DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is not set.');
  console.error('Usage: DATABASE_URL="postgres://user:pass@host:port/dbname?sslmode=require" node scripts/run_migrations.cjs');
  process.exit(1);
}

async function runFlywayMigrations() {
  console.log('Connecting to PostgreSQL database:', DATABASE_URL.replace(/:[^:@]+@/, ':***@'));
  
  const parsed = new URL(DATABASE_URL);
  const client = new Client({
    host: parsed.hostname,
    port: parseInt(parsed.port || '5432', 10),
    user: decodeURIComponent(parsed.username),
    password: decodeURIComponent(parsed.password),
    database: parsed.pathname.replace(/^\//, ''),
    ssl: { rejectUnauthorized: false }
  });

  await client.connect();
  console.log('Connected successfully!');

  // 1. Create flyway_schema_history table if not exists
  await client.query(`
    CREATE TABLE IF NOT EXISTS flyway_schema_history (
      installed_rank INT NOT NULL,
      version VARCHAR(50),
      description VARCHAR(200) NOT NULL,
      type VARCHAR(20) NOT NULL,
      script VARCHAR(1000) NOT NULL,
      checksum INT,
      installed_by VARCHAR(100) NOT NULL,
      installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      execution_time INT NOT NULL,
      success BOOLEAN NOT NULL,
      CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
    );
    CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history (success);
  `);

  // Check existing applied migrations
  const existingRes = await client.query('SELECT version FROM flyway_schema_history WHERE success = true');
  const appliedVersions = new Set(existingRes.rows.map(r => r.version));
  console.log('Already applied migrations:', Array.from(appliedVersions));

  const migrationDir = path.join(__dirname, '../backend/src/main/resources/db/migration');
  const files = fs.readdirSync(migrationDir).filter(f => f.endsWith('.sql')).sort();

  let rank = appliedVersions.size;

  for (const file of files) {
    const match = file.match(/^V(\d+(?:_\d+)*)__(.+)\.sql$/);
    if (!match) {
      console.warn('Skipping file not matching Flyway pattern:', file);
      continue;
    }

    const rawVersion = match[1];
    const version = rawVersion.replace(/_/g, '.');
    const description = match[2].replace(/_/g, ' ');

    if (appliedVersions.has(version)) {
      console.log(`[SKIPPED] ${file} (already applied)`);
      continue;
    }

    console.log(`\n======================================================`);
    console.log(`[RUNNING] ${file} -> Version: ${version}, Description: ${description}`);
    console.log(`======================================================`);

    const filePath = path.join(migrationDir, file);
    const sqlContent = fs.readFileSync(filePath, 'utf-8');

    rank++;
    const startTime = Date.now();

    try {
      await client.query('BEGIN');
      await client.query(sqlContent);
      const executionTime = Date.now() - startTime;

      await client.query(`
        INSERT INTO flyway_schema_history
        (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
        VALUES ($1, $2, $3, 'SQL', $4, NULL, 'avnadmin', CURRENT_TIMESTAMP, $5, true)
      `, [rank, version, description, file, executionTime]);

      await client.query('COMMIT');
      console.log(`[SUCCESS] ${file} applied in ${executionTime}ms`);
    } catch (err) {
      await client.query('ROLLBACK');
      console.error(`[ERROR] Failed to apply ${file}:`, err.message);
      // Record failure if rank not committed
      throw err;
    }
  }

  // Verification
  console.log('\n================ VERIFICATION ================');
  const tablesRes = await client.query(`
    SELECT table_name 
    FROM information_schema.tables 
    WHERE table_schema = 'public' 
    ORDER BY table_name;
  `);
  console.log('Public tables created:', tablesRes.rows.map(r => r.table_name));

  for (const table of tablesRes.rows.map(r => r.table_name)) {
    try {
      const countRes = await client.query(`SELECT COUNT(*) as count FROM "${table}"`);
      console.log(`- ${table}: ${countRes.rows[0].count} rows`);
    } catch (e) {
      console.log(`- ${table}: unable to count (${e.message})`);
    }
  }

  const historyRes = await client.query('SELECT installed_rank, version, description, execution_time, success FROM flyway_schema_history ORDER BY installed_rank');
  console.log('\nFlyway schema history:');
  console.table(historyRes.rows);

  await client.end();
  console.log('\nFlyway migrations from scratch completed successfully!');
}

runFlywayMigrations().catch(err => {
  console.error('Fatal migration error:', err);
  process.exit(1);
});
