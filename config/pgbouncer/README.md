# PgBouncer Configuration for Heronix Talk

PgBouncer is a lightweight connection pooler for PostgreSQL that enables scaling to 500+ concurrent users.

## Why PgBouncer?

| Without PgBouncer | With PgBouncer |
|-------------------|----------------|
| Each user = 1 DB connection | Many users share fewer connections |
| 500 users = 500 connections | 500 users = ~50 connections |
| PostgreSQL max_connections limit | Virtually unlimited client connections |
| High memory per connection (~10MB) | Low memory overhead |

## Installation

### Ubuntu/Debian
```bash
sudo apt-get install pgbouncer
```

### CentOS/RHEL
```bash
sudo yum install pgbouncer
```

### Docker
```bash
docker run -d \
  --name pgbouncer \
  -p 6432:6432 \
  -v $(pwd)/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini \
  -v $(pwd)/userlist.txt:/etc/pgbouncer/userlist.txt \
  edoburu/pgbouncer
```

## Configuration

### 1. Copy configuration files
```bash
sudo cp pgbouncer.ini /etc/pgbouncer/
sudo cp userlist.txt /etc/pgbouncer/
sudo chown pgbouncer:pgbouncer /etc/pgbouncer/*
```

### 2. Generate password hash
```bash
# For user "heronix" with password "your_password"
echo -n "your_passwordheronix" | md5sum
# Output: c5a5e3388d27f7f5c8a8f3e9d0b1a2c3
# Add "md5" prefix in userlist.txt
```

### 3. Update userlist.txt
```
"heronix" "md5c5a5e3388d27f7f5c8a8f3e9d0b1a2c3"
```

### 4. Start PgBouncer
```bash
sudo systemctl start pgbouncer
sudo systemctl enable pgbouncer
```

## Update Heronix Talk Configuration

Change `application-postgresql.properties`:

```properties
# Connect to PgBouncer instead of PostgreSQL directly
spring.datasource.url=jdbc:postgresql://localhost:6432/heronix_talk

# Important: Disable connection test query (PgBouncer handles this)
spring.datasource.hikari.connection-test-query=

# Reduce pool size (PgBouncer does the pooling)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

## Monitoring

### Check pool status
```bash
psql -h localhost -p 6432 -U pgbouncer_stats pgbouncer -c "SHOW POOLS;"
```

### Check client connections
```bash
psql -h localhost -p 6432 -U pgbouncer_stats pgbouncer -c "SHOW CLIENTS;"
```

### Check server connections
```bash
psql -h localhost -p 6432 -U pgbouncer_stats pgbouncer -c "SHOW SERVERS;"
```

### View statistics
```bash
psql -h localhost -p 6432 -U pgbouncer_stats pgbouncer -c "SHOW STATS;"
```

## Pool Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `session` | Connection held for entire session | Legacy apps with session state |
| `transaction` | Connection held per transaction | **Recommended for Heronix Talk** |
| `statement` | Connection held per statement | Very aggressive, may break some queries |

## Scaling Guidelines

| Users | PgBouncer Pool | PostgreSQL max_conn |
|-------|----------------|---------------------|
| 200 | default_pool_size=20 | 100 |
| 500 | default_pool_size=25 | 100 |
| 1000 | default_pool_size=50 | 150 |
| 2000+ | Multiple PgBouncer instances | 200 |

## Troubleshooting

### Connection refused
```bash
# Check if PgBouncer is running
sudo systemctl status pgbouncer

# Check logs
sudo tail -f /var/log/pgbouncer/pgbouncer.log
```

### Authentication failed
```bash
# Verify password hash
echo -n "passwordusername" | md5sum
# Compare with userlist.txt
```

### Pool exhausted
```bash
# Increase pool size in pgbouncer.ini
default_pool_size = 50
max_db_connections = 150

# Reload config
sudo systemctl reload pgbouncer
```

## Architecture with PgBouncer

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Heronix Talk   │     │   PgBouncer     │     │   PostgreSQL    │
│   (500 users)   │────▶│   (Port 6432)   │────▶│   (Port 5432)   │
│                 │     │  Pool: 50 conn  │     │  max_conn: 100  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │
        │ 500 client            │ 50 server
        │ connections           │ connections
        ▼                       ▼
   Application              Database
   handles many           handles fewer
   connections            connections
```
