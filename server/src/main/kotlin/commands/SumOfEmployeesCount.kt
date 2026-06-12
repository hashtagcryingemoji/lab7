package commands

import Response
import ServerContainer
import data.Result

class SumOfEmployeesCount : Command {
    override val name = "sum_of_employees_count"
    override val args = listOf<String>()
    override val description = "Возвращает количество работяг во всей коллекции"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val count = context.dBManager.sumEmployees()
        return Result(true, "Общее количество работяг в коллекции: $count")
    }
}
