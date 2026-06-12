package commands

import data.Result
import ServerContainer
import application.buildOrganization
import data.OrganizationTransferData

class RemoveGreater : Command {
    override val name = "remove_greater"
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
    override val description = "Удаляет из коллекции все элементы, превышающие заданный"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val dbManager = context.dBManager
        return try {
            val org: OrganizationTransferData = buildOrganization(args)
            val count = dbManager.removeGreater(org, userHash)

            Result(true, "Из коллекции удалено $count элементов")
        } catch (_: Exception) {Result(false, "Возникла ошибка.")}
    }
}
