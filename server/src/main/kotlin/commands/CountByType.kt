package commands

import Response
import ServerContainer
import data.Result
import domain.OrganizationType

class CountByType : Command {
    override val name = "count_by_type"
    override val args = listOf("Type")
    override val description = "Подсчитывает количество организаций заданного типа"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val dbManager = context.dBManager
        val neatArgument = args[0].uppercase().trim().replace(" ", "_")
        val waitIsItTrue = OrganizationType.entries.any { it.toString() == neatArgument }
        return if (!waitIsItTrue) Result(false, "Нет такого типа.")
        else {
            val count = dbManager.countType(OrganizationType.valueOf(neatArgument))
            Result(true, count.toString())
        }
    }
}
