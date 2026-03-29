# HibernateBreakingChangeDemo

This repository contains a single demo project with a single difference in version.

This repository demonstrates the potentially jarring result of the change in how Hibernate handles versioning
the ID property.

https://docs.jboss.org/hibernate/orm/6.6/migration-guide/migration-guide.html#merge-versioned-deleted

This repository contains a single unit test in the `DemoApplicationTests` file.

This test demonstrates the change in behavior as commented.

To execute, simply run `./gradle build`

## Versions

- Spring Boot 3.3.9 = hibernate-core:6.5.3.Final
- Spring Boot 3.4.0 = hibernate-core:6.6.2.Final