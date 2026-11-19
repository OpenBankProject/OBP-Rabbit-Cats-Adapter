package com.tesobe.obp.adapter

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/** The contract between the adapter and OBP-API, checked offline.
  *
  * `obp-api-contract/contract.json` lists every OBP-API call the adapter makes, with the response fields the code
  * reads. `obp-api-contract/snapshot.json` is what an OBP-API documents for those calls, written by
  * `obp-api-contract/snapshot.py`. No test here needs a server.
  */
class ObpApiContractTest extends AnyFlatSpec with Matchers {
  import ObpApiContractTest._

  "Every call in the contract" should "be in its source file" in {
    val gone = for {
      call <- calls
      needle <- strings(call, "needles")
      if !read(str(call, "source")).contains(needle)
    } yield s"${callKey(call)}: $needle"
    gone shouldBe Nil
  }

  "Every OBP-API path in the source" should "be a listed call or a listed non-call" in {
    val listed = calls.flatMap(c => strings(c, "needles").map(n => (str(c, "source"), n)))
    val notCalls = notCallsList.map(n => (str(n, "source"), str(n, "fragment")))
    val unlisted = for {
      (file, number, line) <- obpPathLines
      if !listed.exists { case (f, n) => f == file && line.contains(n) }
      if !notCalls.exists { case (f, n) => f == file && line.contains(n) }
    } yield s"$file:$number ${line.trim}"
    unlisted shouldBe Nil
  }

  it should "still have every line listed as a non-call" in {
    val gone = notCallsList.filterNot(n => read(str(n, "source")).contains(str(n, "fragment")))
    gone.map(str(_, "fragment")) shouldBe Nil
  }

  "The snapshot" should "be taken for this contract" in {
    // snapshot.json must hold exactly the calls of contract.json; otherwise run snapshot.py again.
    calls.map(callKey).toSet shouldBe snapshot.map(callKey).toSet
  }

  "Every call" should "reach an endpoint OBP-API documents" in {
    val undocumented = calls.filter { c =>
      snapshot.find(callKey(_) == callKey(c)).exists(_.hcursor.get[Boolean]("missing").getOrElse(false))
    }
    undocumented.map(callKey) shouldBe Nil
  }

  "Every field the code reads" should "be documented unless the contract says it is not" in {
    val problems = for {
      call <- calls
      entry <- snapshot.find(callKey(_) == callKey(call)).toList
      documented = strings(entry, "fields").toSet
      field <- strings(call, "reads")
      accepted = strings(call, "undocumented_fields").contains(field)
      if !documented.contains(field) && !accepted
    } yield s"${callKey(call)}: $field is not documented"
    problems shouldBe Nil
  }

  it should "leave undocumented_fields only for fields still undocumented" in {
    val documentedNow = for {
      call <- calls
      entry <- snapshot.find(callKey(_) == callKey(call)).toList
      field <- strings(call, "undocumented_fields")
      if strings(entry, "fields").contains(field)
    } yield s"${callKey(call)}: $field is documented now; remove it from undocumented_fields"
    documentedNow shouldBe Nil
  }

  "The path scan" should "see a path built from pieces and leave comments alone" in {
    buildsObpPath("""val u = s"${base}/obp/v6.0.0/message-docs/x"""") shouldBe true
    buildsObpPath("""val u = base + "/obp/" + version + "/banks"""") shouldBe true
    buildsObpPath(" * Based on OBP Message Docs: /obp/v6.0.0/message-docs/x") shouldBe false
    buildsObpPath("// GET /obp/v6.0.0/message-docs/x") shouldBe false
    buildsObpPath("val u = 1") shouldBe false
  }

  "The field check" should "report a read that the snapshot does not document" in {
    val documented = Set("message_docs", "message_docs[].process")
    List("message_docs[].process", "message_docs[].prosess").filterNot(documented.contains) shouldBe
      List("message_docs[].prosess")
  }
}

object ObpApiContractTest {
  private val root: Path = Paths.get(".").toAbsolutePath.normalize
  private val dir: Path = root.resolve("obp-api-contract")

  def read(relative: String): String =
    new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8)

  private def load(name: String): Json =
    parse(new String(Files.readAllBytes(dir.resolve(name)), StandardCharsets.UTF_8)).fold(e => throw e, identity)

  def str(json: Json, field: String): String = json.hcursor.get[String](field).getOrElse("")
  def strings(json: Json, field: String): List[String] = json.hcursor.get[List[String]](field).getOrElse(Nil)
  def callKey(json: Json): String = s"${str(json, "verb")} ${str(json, "path")}"

  lazy val calls: List[Json] = load("contract.json").hcursor.get[List[Json]]("calls").fold(e => throw e, identity)
  lazy val notCallsList: List[Json] = load("contract.json").hcursor.get[List[Json]]("not_calls").getOrElse(Nil)
  lazy val snapshot: List[Json] = load("snapshot.json").hcursor.get[List[Json]]("endpoints").fold(e => throw e, identity)

  /** Whether a line of Scala builds the path of an OBP-API call: not a comment, and it names `/obp/` in a string. */
  def buildsObpPath(line: String): Boolean = {
    val code = line.trim
    !(code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) && code.contains("/obp/")
  }

  /** The lines of the adapter's own source that build an OBP-API path: (file, line number, line). */
  def obpPathLines: List[(String, Int, String)] = {
    val stream = Files.walk(root.resolve("src/main"))
    try {
      stream.iterator.asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
        .toList
        .sortBy(_.toString)
        .flatMap { p =>
          val rel = root.relativize(p).toString
          read(rel).split("\n", -1).toList.zipWithIndex.collect {
            case (line, i) if buildsObpPath(line) => (rel, i + 1, line)
          }
        }
    } finally stream.close()
  }
}
