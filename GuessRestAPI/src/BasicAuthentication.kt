package com.umut

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.basic
import org.slf4j.LoggerFactory



fun Application.basicAuthentication() {
    val logger = LoggerFactory.getLogger("BasicAuthentication")
    val apiUser: String = this.environment.config
        .propertyOrNull("ktor.api.user")?.getString()!!
    val apiPassword: String = this.environment.config
        .propertyOrNull("ktor.api.password")?.getString()!!


    install(Authentication) {
        basic(Constants.AUTH_TYPE) {
            realm = "ktor"
            validate { credentials ->
                if (credentials.name == apiUser && credentials.password == apiPassword) {
                    logger.info("${credentials.name} is authenticated")
                    UserIdPrincipal(credentials.name)
                } else {
                    logger.warn("${credentials.name} tried to access server")
                    null
                }
            }
        }
    }

}