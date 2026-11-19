package com.tesobe.obp.adapter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/** Every OBP-API path the adapter and its documentation name uses version v5.1.0 or newer, so a deployment can
  * switch the older versions off without breaking the adapter or sending a reader to a URL that no longer answers.
  * The test reads the source, the scripts and the documents, and fails on any `obp/vX.Y.Z` below v5.1.0.
  */
class ObpApiVersionTest extends AnyFlatSpec with Matchers {
  import ObpApiVersionTest._

  "The adapter's files" should "be found" in {
    // So an empty result below means "no old version", not "no files"
    files.size should be > 5
  }

  "Every OBP-API path in them" should "use v5.1.0 or newer" in {
    val old = for {
      file <- files
      (line, number) <- lines(file).zipWithIndex
      version <- versionsBelow510(line)
    } yield s"${root.relativize(file)}:${number + 1} $version"
    old shouldBe Nil
  }

  "The version check" should "recognise a version below v5.1.0 however the path is written" in {
    versionsBelow510("GET /obp/v2.2.0/message-docs/x") shouldBe List("v2.2.0")
    versionsBelow510("curl https://api/obp/v3.1.0/message-docs/x/swagger2.0") shouldBe List("v3.1.0")
    versionsBelow510("""s"$u/obp/v5.0.0/banks" and "/obp/v4.0.0/x"""") shouldBe List("v5.0.0", "v4.0.0")
    versionsBelow510("{{base_url}}/obp/v1.2.1/banks") shouldBe List("v1.2.1")
  }

  it should "leave v5.1.0 and newer alone" in {
    versionsBelow510("/obp/v5.1.0/root") shouldBe Nil
    versionsBelow510("/obp/v6.0.0/message-docs/x") shouldBe Nil
    versionsBelow510("/obp/v10.0.0/x") shouldBe Nil
    versionsBelow510("the OBP version 4.0.0 of the doc") shouldBe Nil
  }
}

object ObpApiVersionTest {
  val root: Path = Paths.get(".").toAbsolutePath.normalize
  private val Version = """obp/v(\d+)\.(\d+)\.(\d+)(?![\w.])""".r
  private val Extensions = Set(".scala", ".sh", ".md", ".json", ".yml", ".yaml", ".properties", ".conf")
  private val Skipped = Set("target", ".git", "node_modules")

  def versionsBelow510(line: String): List[String] =
    Version.findAllMatchIn(line).toList.collect {
      case m if Ordering[(Int, Int, Int)].lt((m.group(1).toInt, m.group(2).toInt, m.group(3).toInt), (5, 1, 0)) =>
        s"v${m.group(1)}.${m.group(2)}.${m.group(3)}"
    }

  /** The files of the repository that can name an OBP-API path. The contract's own snapshot is generated. */
  lazy val files: List[Path] = {
    val stream = Files.walk(root)
    try {
      stream.iterator.asScala
        .filter(p => Files.isRegularFile(p))
        .filter(p => !root.relativize(p).iterator.asScala.exists(part => Skipped.contains(part.toString)))
        .filter(p => Extensions.exists(p.toString.endsWith))
        .filter(p => root.relativize(p).toString != "obp-api-contract/snapshot.json")
        // This file spells old versions out on purpose, to test the check
        .filter(p => p.getFileName.toString != "ObpApiVersionTest.scala")
        .toList
        .sortBy(_.toString)
    } finally stream.close()
  }

  def lines(file: Path): List[String] =
    new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n", -1).toList
}
