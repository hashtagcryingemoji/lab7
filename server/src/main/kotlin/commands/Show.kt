package commands

import data.Result
import ServerContainer

class Show : Command {
    override val description: String = "Выводит список всех организаций"
    override val args = listOf<String>()
    override val name: String = "show"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val collection = context.dBManager.getCollection()
        return if (collection
                .isEmpty()
        ) Result(true, "Вы еще не успели насоздавать шедевров...")
        else {
            val strBuilder = StringBuilder()
            collection.forEach { strBuilder.append(it); strBuilder.append("\n") }
            Result(true, strBuilder.toString())
        }
    }
}
