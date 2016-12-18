package xyz.jadonfowler.leer

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import java.io.File

fun main(args: Array<String>) {
    Vertx.vertx().deployVerticle(LeerVerticle())
}

class LeerVerticle() : AbstractVerticle() {

    val get_html = File("static/get.html").readLines().joinToString("\n")
    val post_html = File("static/post.html").readLines().joinToString("\n")

    fun createRouter(): Router {
        val router = Router.router(vertx)

        // Used for forms
        router.route().handler(BodyHandler.create())

        router.get("/").handler { r ->
            r.response().end(get_html)
        }

        router.post("/").handler { r ->
            // Get code from form
            val code = r.request().getParam("code")

            // Save code to file
            val codeFile = File("bin/module.c")
            val outputFile = File("bin/module.ll")
            if (!codeFile.exists()) {
                codeFile.parentFile.mkdirs()
                codeFile.createNewFile()
            }
            codeFile.printWriter().use { out -> out.write(code) }

            // Compile code
            val process = Runtime.getRuntime().exec("clang -S -emit-llvm bin/module.c -o bin/module.ll")
            val rc = process.waitFor()

            val outputStream = String(process.inputStream.readBytes())
            val errorStream = String(process.errorStream.readBytes())

            val output: String

            if (rc == 0) {
                // ASMifier the class file`
                if (outputFile.exists()) {
                    output = outputFile.readText()
                } else output = "Couldn't find output file"
            } else {
                // javac returned with an error, return the output & error streams
                output = outputStream + "\n\n" + errorStream
            }

            // Remove files
            codeFile.delete()
            File("bin/module.ll").delete()

            // Build response
            val response = post_html
                    .replace("\$INPUT\$", code)
                    .replace("\$OUTPUT\$", output)

            r.response().end(response)
        }

        return router
    }

    override fun start(fut: Future<Void>) {
        val router = createRouter()
        val portValue = System.getenv("PORT")
        val port = if (portValue.isNullOrEmpty()) 8080 else portValue.toInt()
        vertx
                .createHttpServer()
                .requestHandler({ r -> router.accept(r) })
                .listen(port) { result ->
                    if (result.succeeded()) fut.complete()
                    else fut.fail(result.cause())
                }
        println("Server started: http://localhost:$port/")
    }

}
