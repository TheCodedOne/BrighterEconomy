package brightspark.brightereconomy.rest

import brightspark.brightereconomy.BrighterEconomy
import brightspark.brightereconomy.economy.EconomyState
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

object ApiController {
	private var engine: Optional<NettyApplicationEngine> = Optional.empty()

	fun init() {
		// TODO: Make config for if API is enabled
		if (engine.isPresent) return
		BrighterEconomy.LOG.atInfo().setMessage("Starting REST server").log()
		engine = Optional.of(create())
	}

	fun shutdown() {
		if (engine.isEmpty) return
		BrighterEconomy.LOG.atInfo().setMessage("Stopping REST server").log()
		engine.get().stop()
		engine = Optional.empty()
	}

	private fun create(): NettyApplicationEngine =
		// TODO: Make port configurable
		embeddedServer(Netty, port = 25570) {
			install(ContentNegotiation) { json() }
			routes()
		}.start()

	private fun Application.routes() = routing {
		route("/accounts") {
			get {
				val state = EconomyState.get()
				if (state.isPresent)
					call.respond(state.get().getAccounts().toTypedArray())
				else
					call.respondText("MinecraftServer not available", status = HttpStatusCode.InternalServerError)
			}
			get("{uuid?}") {
				val uuid = call.parameters["uuid"]?.let { UUID.fromString(it) }
					?: return@get call.respondText("Missing UUID", status = HttpStatusCode.BadRequest)
				val state = EconomyState.get()
				if (state.isPresent)
					call.respond(state.get().getAccount(uuid))
				else
					call.respondText("MinecraftServer not available", status = HttpStatusCode.InternalServerError)
			}
		}

		route("/transactions") {
			get {
				val state = EconomyState.get()
				if (state.isPresent)
					call.respond(state.get().getTransactions().toTypedArray())
				else
					call.respondText("MinecraftServer not available", status = HttpStatusCode.InternalServerError)
			}
			get("{uuid?}") {
				val uuid = call.parameters["uuid"]?.let { UUID.fromString(it) }
					?: return@get call.respondText("Missing UUID", status = HttpStatusCode.BadRequest)
				val state = EconomyState.get()
				if (state.isPresent)
					call.respond(state.get().getAccountTransactions(uuid))
				else
					call.respondText("MinecraftServer not available", status = HttpStatusCode.InternalServerError)
			}
		}
	}
}
