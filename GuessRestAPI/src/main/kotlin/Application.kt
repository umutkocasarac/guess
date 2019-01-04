package com.umut

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.http.*
import com.fasterxml.jackson.databind.*
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.protobuf.ByteString
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.tomcat.EngineMain.main(args)

@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val logger = LoggerFactory.getLogger("Application")


    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        anyHost()
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer({ configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) })
        }
    }

    routing {

        install(StatusPages) {
            exception<AuthenticationException> { cause ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { cause ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }

        route("check") {
            get {
                logger.info("check is working")
                val filePath = "/Users/umut/tmp/ktor/aaa.jpg"
                logger.info(getLabels(filePath).get(0))
                logger.info("Completed")

            }
        }

        route("/guess") {

            post {
                val multipart = call.receiveMultipart()
                var title = ""
                var filePath = ""
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "title") {
                            title = part.value
                        }
                        is PartData.FileItem -> {
                            val name = part.originalFileName!!
                            val file = File.createTempFile(UUID.randomUUID().toString(), "")
                            logger.info("File will be created to ${file.path}")
                            filePath = file.path
                            part.streamProvider().use { its ->
                                file.outputStream().buffered().use {
                                    its.copyTo(it)
                                }
                            }
                        }
                    }
                    part.dispose()

                }
                val labels = getLabels(filePath)
                logger.info("Labels are " + labels)
                call.respond(mapOf("guesses" to labels))
            }
        }
    }
}

fun getLabels(filePath: String): List<String> {
    val requests = ArrayList<AnnotateImageRequest>()
    val imgBytes = ByteString.readFrom(FileInputStream(filePath))
    val img = Image.newBuilder().setContent(imgBytes).build()
    val feat =
        com.google.cloud.vision.v1.Feature.newBuilder().setType(com.google.cloud.vision.v1.Feature.Type.WEB_DETECTION)
            .build()
    val request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()
    requests.add(request)
    val client = ImageAnnotatorClient.create()
    val response = client.batchAnnotateImages(requests)
    val responses = response.getResponsesList()
    return responses.getOrNull(0)!!.webDetection.webEntitiesList.filter { e -> e.description.length > 0 }
        .map { e -> e.description }.toMutableList()
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

data class User(var page: Int, var per_Page: Int)
