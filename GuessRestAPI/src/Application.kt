package com.umut

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.protobuf.ByteString
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.jackson
import io.ktor.request.path
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.io.FileInputStream
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.tomcat.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val logger = LoggerFactory.getLogger("Application")
    val tmpDirectory: String = System.getenv("java.io.tmpDir") ?: "/tmp"

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

    basicAuthentication()

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

        authenticate(Constants.AUTH_TYPE) {

            route("/guess") {

                post {
                    logger.info("Start")
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
                                filePath = "$tmpDirectory/$name"
                                logger.debug("File will be created to $filePath")
                                val file = File(filePath)
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
    val responses = response.responsesList
    return responses.getOrNull(0)!!.webDetection.webEntitiesList.filter { e -> e.description.length > 0 }
        .map { e -> e.description }.toMutableList()
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()


