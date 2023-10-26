package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import api.plugins.shouldBeAtLeastMaintainer
import infra.common.UserRepository
import infra.model.User
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/user")
private class UserRes {
    @Resource("")
    class List(
        val parent: UserRes,
        val page: Int,
        val pageSize: Int,
        val role: User.Role,
    )

    @Resource("/{userId}")
    class Id(val parent: UserRes)
}

fun Route.routeUser() {
    val service by inject<UserApi>()

    authenticateDb {
        get<UserRes.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listUser(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    role = loc.role,
                )
            }
        }
    }
}

class UserApi(
    private val userRepo: UserRepository,
) {
    @Serializable
    data class UserOutlineDto(
        val id: String,
        val email: String,
        val username: String,
        val role: User.Role,
        val createdAt: Long,
    )

    suspend fun listUser(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
        role: User.Role,
    ): PageDto<UserOutlineDto> {
        user.shouldBeAtLeastMaintainer()
        return userRepo.listUser(
            page = page,
            pageSize = pageSize,
            role = role,
        ).asDto(pageSize) {
            UserOutlineDto(
                id = it.id.toHexString(),
                email = it.email,
                username = it.username,
                role = it.role,
                createdAt = it.createdAt.epochSeconds,
            )
        }
    }
}
