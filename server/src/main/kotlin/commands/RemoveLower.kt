package commands

import data.Result
import ServerContainer
import application.buildOrganization
import data.OrganizationTransferData

class RemoveLower : Command {
    override val name = "remove_lower"
    override val args = listOf(
        "Name",
        "X",
        "Y",
        "Annual turnover",
        "Full name (unique)",
        "Employee count",
        "Street",
        "Zip code",
        "Type"
    )
    override val description = "Удаляет из коллекции все элементы, меньше чем"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val dbManager = context.dBManager
        return try {
            val org: OrganizationTransferData = buildOrganization(args)
            val count = dbManager.removeLower(org, userHash)

            Result(true, "Из коллекции удалено $count элементов")
        } catch (e: IllegalStateException) {Result(false, e.message ?: "хз")}
        catch (_: NumberFormatException) {Result(false, "Некорректное числовое представление.")}
    }
}
