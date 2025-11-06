# SQL Spark Connector - Spark 4.0 Migration Analysis

## Executive Summary

The sql-spark-connector is currently deprecated and was last maintained for Spark 3.4.x. To make it work with Spark 4.0, we need to address Scala version incompatibility and verify compatibility with Spark 4.0 APIs.

## Current Error Analysis

### Error Observed in Databricks (Spark 4.0)

```
Py4JJavaError: An error occurred while calling o672.save.
: java.lang.NoClassDefFoundError: scala/Serializable
    at com.microsoft.sqlserver.jdbc.spark.DefaultSource.createRelation(DefaultSource.scala:54)
Caused by: java.lang.ClassNotFoundException: scala.Serializable
```

### Root Cause

This is a **binary incompatibility issue** between Scala versions:

1. **Current State**: The connector is compiled with Scala 2.12 (as seen in pom.xml:210)
2. **Spark 4.0 Requirement**: Spark 4.0 requires Scala 2.13
3. **The Problem**: When a JAR compiled with Scala 2.12 is loaded in a Spark 4.0 (Scala 2.13) environment, it fails because:
   - In Scala 2.12: `scala.Serializable` was a separate trait
   - In Scala 2.13: `scala.Serializable` is now a type alias for `java.io.Serializable`
   - JARs compiled with Scala 2.12 contain bytecode references to the old `scala.Serializable` class, which doesn't exist in Scala 2.13 runtime

## Project Structure Overview

### Key Components

1. **Entry Point**: `DefaultSource.scala` - Extends JdbcRelationProvider, implements DataSourceV1 API
2. **Core Logic**:
   - `Connector.scala` - Abstract base class for write operations
   - `BulkCopyUtils.scala` - Implements SQL Server bulk copy operations
   - `ConnectorFactory.scala` - Factory for creating appropriate connector instances
3. **Strategies**:
   - `SingleInstanceConnector.scala` - For single SQL Server instances
   - `DataPoolConnector.scala` - For SQL Server Big Data Clusters
4. **Build System**: Maven (pom.xml)

### Current Build Configuration

From `pom.xml` (lines 203-226):
```xml
<profile>
    <id>spark34</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
        <scala.binary.version>2.12</scala.binary.version>
        <scala.version>2.12.11</scala.version>
        <spark.version>3.4.0</spark.version>
    </properties>
    ...
</profile>
```

## Spark 4.0 Requirements

Based on official Spark 4.0 documentation:

1. **Scala Version**: Scala 2.13 (Scala 2.12 support dropped)
2. **Java Version**: Java 17 or 21
3. **API Compatibility**: DataSourceV1 API is still supported for backward compatibility
4. **Breaking Changes**:
   - Various deprecated APIs removed
   - Hive metastore < 2.0.0 no longer supported
   - Apache Mesos support removed
   - Some codec name changes (e.g., lz4raw -> lz4_raw)

## Migration Plan

### Phase 1: Update Build Configuration

1. **Create new Maven profile for Spark 4.0**
   - Add `spark40` profile in pom.xml
   - Set Scala version to 2.13.12 (latest stable 2.13.x)
   - Set Spark version to 4.0.0 or 4.0.1
   - Update scala.binary.version to 2.13

2. **Update Dependencies**
   - Verify mssql-jdbc driver compatibility (currently 8.4.1.jre8)
   - Update to mssql-jdbc 12.x which supports Java 17/21
   - Update scalatest to version compatible with 2.13

3. **Update Compiler Settings**
   - Ensure Java source/target is set to 17 or 21
   - Update scala-maven-plugin configuration if needed

### Phase 2: Code Compatibility Review

Since the connector uses DataSourceV1 API which is maintained in Spark 4.0, minimal code changes should be required:

1. **Review API Usage**
   - Check `org.apache.spark.sql.execution.datasources.jdbc.JdbcRelationProvider`
   - Verify `org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions` compatibility
   - Review any deprecated API usage

2. **Test Data Type Mappings**
   - Spark 4.0 changed overflow behavior for timestamp casting
   - Verify type conversions in `BulkCopyUtils.scala`

3. **Check Serialization**
   - Since this is a connector that runs on executors, ensure all classes passed to executors are properly serializable
   - No explicit `scala.Serializable` references found in codebase (good!)

### Phase 3: Build and Deployment

1. **Build Artifact**
   - Build with Maven: `mvn clean package -Pspark40`
   - Generate artifact: `spark-mssql-connector_2.13-1.5.0.jar`

2. **Documentation Updates**
   - Update README.md with Spark 4.0 support
   - Add migration guide for users upgrading from 3.4.x
   - Update Maven coordinates table

## Implementation Steps (Detailed)

### Step 1: Add Spark 4.0 Profile to pom.xml

Add the following profile after the existing `spark34` profile:

```xml
<profile>
    <id>spark40</id>
    <properties>
        <scala.binary.version>2.13</scala.binary.version>
        <scala.version>2.13.12</scala.version>
        <spark.version>4.0.1</spark.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.scalatest</groupId>
            <artifactId>scalatest_${scala.binary.version}</artifactId>
            <version>3.2.18</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>12.6.1.jre11</version>
        </dependency>
    </dependencies>
</profile>
```

### Step 2: Update Java Version

Update the maven-compiler-plugin configuration:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.7.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
    </configuration>
</plugin>
```

### Step 3: Build for Spark 4.0

```bash
# Build with Spark 4.0 profile
mvn clean compile -Pspark40

# If successful, package
mvn clean package -Pspark40

# Skip tests initially if they fail
mvn clean package -Pspark40 -DskipTests
```

## Potential Issues and Solutions

### Issue 1: API Deprecations

**Problem**: Some Spark APIs may have been removed in 4.0
**Solution**: Review deprecation warnings during compilation, update to new APIs

### Issue 2: JDBC Driver Compatibility

**Problem**: Old JDBC driver may not work with Java 17/21
**Solution**: Updated to mssql-jdbc 12.6.1.jre11 in the plan

### Issue 3: Type System Changes

**Problem**: Spark 4.0 has stricter type checking
**Solution**: Review and fix any type inference issues that arise

### Issue 4: Serialization Issues

**Problem**: Executor serialization failures
**Solution**: Ensure all closure variables are serializable, avoid capturing non-serializable objects

## Success Criteria

1. **Compilation**: Code compiles without errors with Scala 2.13 and Spark 4.0
2. **Artifact Generation**: Successfully builds `spark-mssql-connector_2.13-1.5.0.jar`
3. **No ClassNotFoundException**: Resolves the original `scala.Serializable` error
4. **API Compatibility**: All DataSourceV1 API usage remains compatible with Spark 4.0

## Timeline Estimate

- **Phase 1**: 2-4 hours (build configuration updates)
- **Phase 2**: 2-4 hours (code review and minor fixes)
- **Phase 3**: 1-2 hours (build and packaging)

**Total**: 5-10 hours of development work

## Risks and Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Breaking API changes in Spark 4.0 | High | Low | DataSourceV1 is maintained for compatibility |
| JDBC driver issues | High | Low | Use latest stable driver version |
| Compilation errors with Scala 2.13 | Medium | Low | Review and fix any type inference issues |

## References

- [Apache Spark 4.0 Documentation](https://spark.apache.org/docs/latest/)
- [Spark 4.0 Migration Guide](https://spark.apache.org/docs/latest/migration-guide.html)
- [Scala 2.13 Release Notes](https://github.com/scala/scala/releases/tag/v2.13.12)
- [Microsoft JDBC Driver for SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server)

## Next Steps

1. Review this document with stakeholders
2. Set up development environment with Java 17 and Maven
3. Begin Phase 1: Update build configuration
4. Create feature branch for Spark 4.0 support
5. Build and package the connector for Spark 4.0
