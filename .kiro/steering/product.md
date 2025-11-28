---
inclusion: always
---

# Product Overview

This is a distributed lock cheat sheet project that provides sample implementations of distributed locking patterns using various data stores.

## Supported Lock Types

- **MySQL**: Session-based locks using `GET_LOCK()` / `RELEASE_LOCK()`
- **PostgreSQL**: Advisory locks using `pg_advisory_lock` and `pg_try_advisory_lock`
- **Redis**: Lua script-based locks, `SETNX`-based locks, and Redisson

## Purpose

The project serves as a reference implementation and learning resource for distributed locking mechanisms across different database systems. It demonstrates best practices for handling concurrent operations in distributed systems.
