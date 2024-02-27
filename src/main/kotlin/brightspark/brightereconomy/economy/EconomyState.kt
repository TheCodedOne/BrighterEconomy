package brightspark.brightereconomy.economy

import brightspark.brightereconomy.BrighterEconomy
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import net.minecraft.world.World
import java.util.*

class EconomyState : PersistentState {
	companion object {
		fun get(): Optional<EconomyState> = BrighterEconomy.SERVER.map { get(it) }

		fun get(server: MinecraftServer): EconomyState {
			val manager = server.getWorld(World.OVERWORLD)!!.persistentStateManager
			val state = manager.getOrCreate(::EconomyState, ::EconomyState, BrighterEconomy.MOD_ID)
			state.markDirty()
			return state
		}
	}

	private val accounts = mutableMapOf<UUID, PlayerAccount>()
	private val transactions = mutableMapOf<UUID, MutableList<Transaction>>()

	constructor()

	constructor(nbt: NbtCompound) {
		readNbt(nbt)
	}

	fun getAccountUuids(): Set<UUID> = accounts.keys

	fun getAccounts(): Collection<PlayerAccount> = accounts.values

	fun getAccount(uuid: UUID): PlayerAccount = accounts.getOrPut(uuid) { PlayerAccount(uuid) }

	fun getMoney(uuid: UUID): Long = accounts[uuid]?.money ?: 0

	fun getTransactions(): List<Transaction> = transactions.values.flatten()

	fun getAccountTransactions(uuid: UUID): List<Transaction> = transactions[uuid] ?: emptyList()

	fun exchange(uuidFrom: UUID, uuidTo: UUID, money: Long): Pair<TransactionExchangeResult, Transaction?> {
		BrighterEconomy.LOG.atInfo()
			.setMessage("Attempting to exchange {} from {} to {}")
			.addArgument(money).addArgument(uuidFrom).addArgument(uuidTo)
			.log()

		val from = getAccount(uuidFrom)
		val to = getAccount(uuidTo)
		val result = validateExchange(from, to, money)
		if (result != TransactionExchangeResult.SUCCESS)
			return result to null

		accounts[uuidFrom] = from.copy(money = from.money - money)
		accounts[uuidTo] = to.copy(money = to.money + money)
		BrighterEconomy.LOG.atInfo()
			.setMessage("Exchange success {} from {} to {}")
			.addArgument(money).addArgument(uuidFrom).addArgument(uuidTo)
			.log()
		return TransactionExchangeResult.SUCCESS to Transaction(uuidFrom, uuidTo, money)
	}

	private fun validateExchange(from: PlayerAccount, to: PlayerAccount, money: Long): TransactionExchangeResult {
		if (from.locked) {
			BrighterEconomy.LOG.atWarn().setMessage("Exchange failed due to {} locked").addArgument(from.uuid).log()
			return TransactionExchangeResult.FROM_LOCKED
		}
		if (to.locked) {
			BrighterEconomy.LOG.atWarn().setMessage("Exchange failed due to {} locked").addArgument(to.uuid).log()
			return TransactionExchangeResult.TO_LOCKED
		}
		if (from.money < money) {
			BrighterEconomy.LOG.atWarn()
				.setMessage("Exchange failed due to {} insufficient money ({})")
				.addArgument(from.uuid).addArgument(from.money)
				.log()
			return TransactionExchangeResult.INSUFFICIENT_MONEY
		}
		if (Long.MAX_VALUE - to.money < money) {
			BrighterEconomy.LOG.atWarn()
				.setMessage("Exchange failed due to {} overflow money ({})")
				.addArgument(to.uuid).addArgument(to.money)
				.log()
			return TransactionExchangeResult.OVERFLOW_MONEY
		}
		return TransactionExchangeResult.SUCCESS
	}

	fun lockAccount(uuid: UUID) {
		accounts.compute(uuid) { _, account ->
			account?.copy(locked = true) ?: PlayerAccount(uuid = uuid, locked = true)
		}
		BrighterEconomy.LOG.atInfo().setMessage("Locked account {}").addArgument(uuid).log()
	}

	fun unlockAccount(uuid: UUID) {
		accounts.compute(uuid) { _, account ->
			account?.copy(locked = true) ?: PlayerAccount(uuid = uuid, locked = true)
		}
		BrighterEconomy.LOG.atInfo().setMessage("Unlocked account {}").addArgument(uuid).log()
	}

	private fun readNbt(nbt: NbtCompound) {
		accounts.clear()
		nbt.getList("accounts", NbtElement.COMPOUND_TYPE.toInt()).forEach {
			val account = PlayerAccount(it as NbtCompound)
			accounts[account.uuid] = account
		}
	}

	override fun writeNbt(nbt: NbtCompound): NbtCompound = nbt.apply {
		put("accounts", NbtList().apply { accounts.values.forEach { add(it.writeNbt(NbtCompound())) } })
	}
}
