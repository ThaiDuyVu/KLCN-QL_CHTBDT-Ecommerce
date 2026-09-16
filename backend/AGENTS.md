# Backend Agent Rules

## 1. Core Principles

- Follow existing project conventions before introducing new patterns.
- Inspect related code before modifying or implementing a feature.
- Prefer simple, readable, maintainable solutions.
- Make only changes required by the current task.
- Do not refactor unrelated code.

Do not invent unsupported:

- API contracts
- DTO/entity fields
- database structures or relationships
- business rules
- roles/permissions
- authentication/authorization behavior
- enum/status values

Existing code and the existing database schema are the source of truth.

---

## 2. Architecture

The backend uses feature-based / vertical-slice organization.

Typical feature structure:

```text
feature/
├── controller
├── dto
├── entity
├── exception
├── repository
└── service
```

Cross-cutting concerns belong under `common`.

Expected dependency flow:

```text
Controller
    ↓
Service Interface
    ↓
Service Implementation
    ↓
Repository
    ↓
Entity / Database
```

Controllers must not access repositories directly.

---

## 3. Controller

Controllers must remain thin.

They may:

- receive HTTP input
- trigger request validation
- delegate to services
- return HTTP responses

They must not contain:

- persistence logic
- core business logic
- repository access
- entity mapping
- local handling of expected business exceptions

Use constructor injection.

---

## 4. Service

Use `XService` and `XServiceImpl` in the feature's `service` package.

Services own:

- business logic
- business validation
- repository coordination
- transactions
- DTO mapping/assembly
- domain exception decisions

Read-oriented services should use:

```java
@Transactional(readOnly = true)
```

when appropriate.

Write operations must use an appropriate write transaction.

---

## 5. Repository

Repositories are responsible only for persistence and querying.

Use Spring Data JPA conventions where appropriate.

Repositories must not contain:

- HTTP logic
- controller logic
- business workflow logic

Use `Optional<T>` when absence is an expected lookup outcome.

---

## 6. Entity & DTO

Entities must match the existing database schema.

Entities must not be exposed directly through REST APIs.

REST APIs must use DTOs.

Current DTOs are plain Java classes under:

```text
feature/dto
```

Do not introduce the following without a useful technical reason:

- Java records
- Lombok
- additional DTO package layers
- mapping frameworks

Entity/DTO mapping belongs in services or a dedicated feature mapper when complexity or duplication justifies it.

Controllers must not perform entity mapping.

---

## 7. Validation

Use Bean Validation for request/input constraints when appropriate.

Business validation belongs in services.

Do not invent validation rules unsupported by:

- requirements
- database schema
- existing business logic
- existing project conventions

---

## 8. Exceptions

Feature-specific exceptions belong in:

```text
feature/exception
```

HTTP exception handling must remain centralized in the global exception handler.

Controllers must not catch expected business exceptions.

Follow existing HTTP mappings and response formats.

Do not introduce a new global response/error wrapper unless explicitly requested.

---

## 9. REST API

Follow existing REST conventions.

Prefer plural resource paths.

Examples:

```text
/api/users
/api/products
/api/categories
```

Use HTTP methods and status codes according to endpoint intent.

Do not rename or redesign existing APIs solely for stylistic reasons.

Do not expose Spring Data `Page<T>` directly if dedicated pagination DTOs are already used.

---

## 10. Database Schema — LOCKED

The existing database schema is fixed.

The agent MUST NOT:

- create or remove tables
- add, remove, or rename columns
- modify column types
- modify constraints
- modify indexes
- modify foreign keys
- modify database relationships
- modify existing Flyway migrations
- create new Flyway migrations
- make any other schema change

Existing Flyway migrations may be inspected to understand the database schema.

Backend code must adapt to the existing schema, not the other way around.

If a requested feature cannot be implemented correctly without a schema change:

1. Inspect the existing schema and related implementation.
2. Determine whether a valid solution exists using the current schema.
3. If no valid solution exists, report the exact limitation instead of modifying the database.

Do not modify the database schema unless the user explicitly overrides this rule for the specific task.

---

## 11. Seed & Development Data

The agent MAY create seed/development/test data when useful for:

- local development
- API testing
- integration testing
- pagination testing
- filtering and sorting testing
- relationship testing
- demonstrating business flows

Seed data must conform to existing:

- database schema
- enums/statuses
- roles/permissions
- relationships
- business rules

The agent MAY create multiple realistic records.

Do not create only one record when multiple varied records would test the feature more effectively.

For example, list-oriented features may contain several records with different existing attributes or states so that pagination, filtering, sorting, and other API behavior can be meaningfully tested.

Seed data should be:

- reasonably sized
- varied enough to test meaningful scenarios
- deterministic and reusable when practical
- isolated from production behavior

Seed data MUST NOT:

- change the database schema
- invent unsupported business concepts
- invent new enum/status values
- invent new roles or permissions
- violate existing relationships or constraints
- contain real credentials, secrets, tokens, or personal information

If required seed values cannot be determined from the existing project, do not invent them.
## Seed Data Localization

Seed/development/test data should reflect the project's Vietnamese business context unless a specific feature requires otherwise.

When generating human-readable seed data, prefer realistic Vietnamese-style values, for example:

- Vietnamese names
- Vietnamese product/category names
- Vietnamese addresses or city/province names
- Vietnamese phone-number formats
- VND-oriented prices where monetary examples are needed
- Vietnamese descriptions/status labels only when consistent with the existing schema and business rules

Do not use obviously foreign placeholder data such as `John Doe`, `123 Main Street`, or unrelated US/EU business examples when realistic Vietnamese data would be more appropriate.

Seed data must still follow existing schema constraints, enum values, business rules, and security requirements.

For automated tests where the literal human-readable value is irrelevant, simple deterministic synthetic values are acceptable.
---

## 12. Security

Inspect the existing Spring Security implementation before making security changes.

Never expose or log:

- passwords
- JWTs
- refresh tokens
- secret keys
- authentication credentials

Passwords must never be stored in plaintext.

Do not invent:

- authentication flows
- JWT claims
- token storage strategies
- role semantics
- permissions
- authorization behavior

unless they are explicitly defined by the task or already established by the project.

---

## 13. Libraries & Dependencies

Prefer existing dependencies when they already satisfy the requirement.

Dependencies MAY be:

- added
- removed
- upgraded
- downgraded
- replaced

when there is a clear technical reason.

Valid reasons may include:

- compatibility
- security
- build failures
- runtime problems
- significant maintainability improvement
- functionality genuinely required by the feature

Dependency changes must remain compatible with:

- the current Java version
- the current Spring Boot version
- the existing project stack

Avoid adding libraries for trivial functionality that can be implemented cleanly using the existing stack or standard Java.

Do not introduce unnecessary framework complexity.

---

## 14. Logging

Use the project's logging framework.

Do not use:

```java
System.out.println(...)
```

for application logging.

Logs should be meaningful.

Never log sensitive information.

---

## 15. Testing

Add or update relevant tests when appropriate, especially for:

- important business rules
- security behavior
- non-trivial repository queries
- API behavior
- bug fixes

Prefer behavior-focused tests rather than tests tightly coupled to implementation details.

Seed/test data may contain multiple varied records when this improves test coverage.

---

## 16. Agent Workflow

Before implementing a task:

1. Inspect the related feature and shared code.
2. Inspect the existing database schema/migrations when persistence is involved.
3. Identify existing conventions and reusable components.
4. Determine the smallest correct implementation.
5. Implement using the existing architecture.
6. Update validation, exception handling, and tests where required.
7. Run the relevant build/tests.
8. Review changed files for unnecessary modifications.

If material information cannot be determined from the repository or task requirements, do not silently invent it.

For complex tasks, briefly state the discovered context and implementation approach before making substantial changes.

---

## 17. Change Discipline

Preserve existing behavior unless the requested task requires changing it.

Do not:

- rewrite unrelated working code
- rename unrelated APIs or classes
- move packages unnecessarily
- change established contracts without reason
- perform broad cleanup as part of an unrelated task

Prefer minimal, focused, and consistent changes.