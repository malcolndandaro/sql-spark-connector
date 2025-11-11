// Runtime verification script for Spark 4.0 connector
// Run this in your Spark environment to verify the runtime configuration

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.execution.datasources.jdbc.JdbcOptionsInWrite
import org.apache.spark.sql.catalyst.util.CaseInsensitiveMap

object RuntimeVerification {
  def main(args: Array[String]): Unit = {
    println("=== Spark Runtime Verification ===")

    // Check Spark version
    val spark = SparkSession.builder().getOrCreate()
    println(s"Spark Version: ${spark.version}")
    println(s"Spark Scala Version: ${scala.util.Properties.versionNumberString}")

    // Check if JdbcOptionsInWrite constructor exists
    try {
      val constructor = classOf[JdbcOptionsInWrite].getConstructor(classOf[CaseInsensitiveMap[String]])
      println(s"✓ JdbcOptionsInWrite(CaseInsensitiveMap) constructor found")
      println(s"  Constructor: $constructor")
    } catch {
      case e: NoSuchMethodException =>
        println(s"✗ JdbcOptionsInWrite(CaseInsensitiveMap) constructor NOT found")
        println(s"  Available constructors:")
        classOf[JdbcOptionsInWrite].getConstructors.foreach(c => println(s"    - $c"))
    }

    // Check class loaders
    println(s"\nJdbcOptionsInWrite ClassLoader: ${classOf[JdbcOptionsInWrite].getClassLoader}")
    println(s"CaseInsensitiveMap ClassLoader: ${classOf[CaseInsensitiveMap[_]].getClassLoader}")

    // Try to instantiate
    try {
      val params = Map("url" -> "jdbc:sqlserver://test", "dbtable" -> "test")
      val options = new JdbcOptionsInWrite(params)
      println(s"\n✓ Successfully created JdbcOptionsInWrite instance")
    } catch {
      case e: Exception =>
        println(s"\n✗ Failed to create JdbcOptionsInWrite: ${e.getMessage}")
        e.printStackTrace()
    }

    spark.stop()
  }
}
