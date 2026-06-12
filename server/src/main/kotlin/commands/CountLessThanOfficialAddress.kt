package commands

import Response
import ServerContainer
import data.Result
import domain.Address

class CountLessThanOfficialAddress : Command {
    override val name = "count_less_than_official_address"
    override val args = listOf("Street", "Zip")
    override val description = "Подсчитывает количество организаций чей адрес меньше заданного"

    override fun execute(context: ServerContainer, args: List<String>, userHash: String): Result {
        val dbManager = context.dBManager

        val street = args[0]
        val zip = args[1]
        val address = Address(street, zip)

        val count = dbManager.countLessAddress(address)

        return Result(true, "Организаций с меньшим адресом: $count")
    }
}
