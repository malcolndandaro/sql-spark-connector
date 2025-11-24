# SQL Spark Connector - Spark 4.0 Migration (COMPLETED ✅)

## Executive Summary

This is a **community-maintained fork** of the original Microsoft sql-spark-connector, which was deprecated in February 2025. This fork has been **successfully migrated to support Apache Spark 4.0 and Databricks Runtime 17.3 LTS**. The connector now supports both Spark 3.4.x (Scala 2.12) and Spark 4.0.x (Scala 2.13).

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

## Migration Implementation (COMPLETED)

### Phase 1: Build Configuration ✅

**File: `pom.xml`**

1. **Added Spark 4.0 Maven Profile** (lines 228-249):
   ```xml
   <profile>
       <id>spark40</id>
       <properties>
           <scala.binary.version>2.13</scala.binary.version>
           <scala.version>2.13.12</scala.version>
           <spark.version>4.0.1</spark.version>
           <java.version>17</java.version>
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

2. **Updated Maven Compiler Plugin**:
   - Changed from hardcoded Java 1.8 to `${java.version}` property
   - Allows profile-specific Java version (8 for Spark 3.4, 17 for Spark 4.0)

### Phase 2: Code Compatibility Fixes ✅

**File: `src/main/scala/com/microsoft/sqlserver/jdbc/spark/utils/BulkCopyUtils.scala`**

Fixed three Spark 4.0 API compatibility issues:

1. **Line 305** - `getSchema` method now requires Connection parameter:
   ```scala
   // Before:
   val tableCols = getSchema(rs, JdbcDialects.get(url))

   // After:
   val tableCols = getSchema(conn, rs, JdbcDialects.get(url))
   ```

2. **Line 502** - `schemaString` now takes JdbcDialect as first parameter:
   ```scala
   // Before:
   val strSchema = schemaString(df.schema, true, options.url, options.createTableColumnTypes)

   // After:
   val strSchema = schemaString(JdbcDialects.get(options.url), df.schema, true, options.createTableColumnTypes)
   ```

3. **Line 520** - Same fix for external table creation:
   ```scala
   // Before:
   val strSchema = schemaString(df.schema, true, "jdbc:sqlserver")

   // After:
   val strSchema = schemaString(JdbcDialects.get("jdbc:sqlserver"), df.schema, true)
   ```

**File: `src/main/scala/com/microsoft/sqlserver/jdbc/spark/SQLServerBulkJdbcOptions.scala`**

Fixed constructor parameter handling for Spark 4.0 binary compatibility:

```scala
// Before (lines 24-32):
class SQLServerBulkJdbcOptions(val params: CaseInsensitiveMap[String])
    extends JdbcOptionsInWrite(params) {
  def this(params: Map[String, String]) = this(CaseInsensitiveMap(params))
  override val parameters = params

// After (lines 24-31):
class SQLServerBulkJdbcOptions(
    parameters: CaseInsensitiveMap[String])
    extends JdbcOptionsInWrite(parameters.originalMap) {
  def this(params: Map[String, String]) = this(CaseInsensitiveMap(params))
  val params: CaseInsensitiveMap[String] = parameters
```

This fix resolves the `NoSuchMethodError` related to `JdbcOptionsInWrite` constructor changes in Spark 4.0.

**File: `src/test/java/com/microsoft/sqlserver/jdbc/spark/bulkwrite/DataSourceUtilsTest.java`**

Updated Scala collection conversions for Scala 2.13:

```java
// Before:
import scala.collection.JavaConversions;
Iterator<Row> itr = JavaConversions.asScalaIterator(Arrays.asList(rows).iterator());

// After:
import scala.jdk.javaapi.CollectionConverters;
Iterator<Row> itr = CollectionConverters.asScala(Arrays.asList(rows).iterator());
```

### Phase 3: Testing & Validation ✅

**Build Status:** ✅ SUCCESS

```bash
mvn clean package -Pspark40
```

**Test Results:**
- ✅ All 9 tests passed (7 Scala + 2 Java)
- ✅ Zero failures, zero errors
- ✅ Build time: ~38 seconds

**Generated Artifacts:**
- `target/spark-mssql-connector_2.13-1.4.0-spark40.jar` (84 KB)

## How to Build

### Prerequisites

- Java 17 or 21
- Maven 3.6+
- Internet connection (for dependency downloads)

### Build Commands

**For Spark 4.0 / Databricks Runtime 17.3 LTS (Scala 2.13):**
```bash
mvn clean package -Pspark40
```

**For Spark 3.4 (Scala 2.12) - Still Supported:**
```bash
mvn clean package -Pspark34
# or simply:
mvn clean package
```

## Issues Encountered & Resolved

### Issue 1: ✅ RESOLVED - Binary Incompatibility (Scala.Serializable)

**Error:**
```
java.lang.NoClassDefFoundError: scala/Serializable
Caused by: java.lang.ClassNotFoundException: scala.Serializable
```

**Root Cause:** Connector compiled with Scala 2.12 being used in Spark 4.0 (Scala 2.13) environment

**Solution:** Recompiled entire connector with Scala 2.13 using the spark40 Maven profile

### Issue 2: ✅ RESOLVED - Constructor Method Signature Mismatch

**Error:**
```
java.lang.NoSuchMethodError: 'void org.apache.spark.sql.execution.datasources.jdbc.JdbcOptionsInWrite.<init>(org.apache.spark.sql.catalyst.util.CaseInsensitiveMap)'
```

**Root Cause:** `SQLServerBulkJdbcOptions` constructor parameter handling conflicted with Spark 4.0's `JdbcOptionsInWrite` changes

**Solution:** Refactored constructor to avoid field override conflicts (see `SQLServerBulkJdbcOptions.scala` changes)

### Issue 3: ✅ RESOLVED - Spark API Method Signature Changes

**Problems:**
- `getSchema()` method signature changed to include Connection parameter
- `schemaString()` method signature changed to take JdbcDialect as first parameter

**Solution:** Updated all three call sites in `BulkCopyUtils.scala` (lines 305, 502, 520)

### Issue 4: ✅ RESOLVED - Scala 2.13 Collection API Changes

**Error:** Compilation error with `scala.collection.JavaConversions`

**Root Cause:** `JavaConversions` removed in Scala 2.13

**Solution:** Migrated to `scala.jdk.javaapi.CollectionConverters` in test files

## Success Criteria - ALL MET ✅

1. ✅ **Compilation**: Code compiles without errors with Scala 2.13 and Spark 4.0
2. ✅ **Artifact Generation**: Successfully builds `spark-mssql-connector_2.13-1.4.0-spark40.jar`
3. ✅ **No ClassNotFoundException**: Resolves the original `scala.Serializable` error
4. ✅ **API Compatibility**: All DataSourceV1 API usage compatible with Spark 4.0
5. ✅ **Test Suite**: All 9 tests pass (7 Scala + 2 Java)
6. ✅ **Databricks Compatible**: Successfully tested on Databricks Runtime 17.3 LTS

## Actual Timeline

- **Phase 1**: ~1 hour (build configuration updates)
- **Phase 2**: ~1 hour (API compatibility fixes + constructor fix)
- **Phase 3**: ~30 minutes (testing & packaging)

**Total**: ~2.5 hours of development work

## Compatibility Matrix

| Spark Version | Scala Version | Java Version | JDBC Driver | Artifact Name | Maven Profile |
|--------------|---------------|--------------|-------------|---------------|---------------|
| 3.4.x | 2.12.11 | 8 | 8.4.1.jre8 | spark-mssql-connector_2.12-1.4.0.jar | spark34 (default) |
| 4.0.x | 2.13.12 | 17/21 | 12.6.1.jre11 | spark-mssql-connector_2.13-1.4.0-spark40.jar | spark40 |

### Verified Runtime Environments

- ✅ Apache Spark 4.0.1 standalone
- ✅ Databricks Runtime 17.3 LTS (Spark 4.0, Scala 2.13)

## References

- [Apache Spark 4.0 Documentation](https://spark.apache.org/docs/latest/)
- [Spark 4.0 Migration Guide](https://spark.apache.org/docs/latest/migration-guide.html)
- [Scala 2.13 Release Notes](https://github.com/scala/scala/releases/tag/v2.13.12)
- [Microsoft JDBC Driver for SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server)

## Usage Instructions

### Installation

**For Databricks:**

1. Build the connector with the spark40 profile:
   ```bash
   mvn clean package -Pspark40
   ```

2. Upload JAR to Databricks:
   - Navigate to: Workspace → Create → Library
   - Upload: `target/spark-mssql-connector_2.13-1.4.0-spark40.jar`
   - Attach to cluster (must be Runtime 17.3 LTS or later with Spark 4.0)

**For Standalone Spark 4.0:**

```bash
cp target/spark-mssql-connector_2.13-1.4.0-spark40.jar $SPARK_HOME/jars/
```

### Example Usage

**Python / PySpark:**
```python
df.write \
    .format("com.microsoft.sqlserver.jdbc.spark") \
    .mode("overwrite") \
    .option("url", "jdbc:sqlserver://your-server.database.windows.net:1433") \
    .option("dbtable", "YourTable") \
    .option("user", "username") \
    .option("password", "password") \
    .save()
```

**Scala / Spark:**
```scala
df.write
  .format("com.microsoft.sqlserver.jdbc.spark")
  .mode("overwrite")
  .option("url", "jdbc:sqlserver://your-server.database.windows.net:1433")
  .option("dbtable", "YourTable")
  .option("user", "username")
  .option("password", "password")
  .save()
```

## Fork Information

This is a **community-maintained fork** of the original Microsoft sql-spark-connector project, which was officially deprecated in February 2025. This fork is maintained independently and includes:

- ✅ Spark 4.0 support (new)
- ✅ Databricks Runtime 17.3 LTS support (new)
- ✅ Continued Spark 3.4 support (maintained)
- ✅ All original features preserved

### Contributing

Contributions are welcome! Please:
1. Fork this repository
2. Create a feature branch
3. Submit a pull request with tests

### Support

For issues or questions:
- Open an issue on the GitHub repository
- See the detailed migration documents:
  - `SPARK40_MIGRATION_COMPLETE.md`
  - `SPARK40_CONSTRUCTOR_FIX.md`
