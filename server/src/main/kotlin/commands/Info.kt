package commands

import Response
import ServerContainer
import data.Result

class Info : Command {
    override val name = "info"
    override val args = listOf<String>()
    override val description = "Выводит информацию о коллекции"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        return Result(
            true,
            context.dBManager.collectionInfo()
        )
    }
}
