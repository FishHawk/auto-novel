package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import domain.entity.Page
import domain.entity.User
import domain.entity.UserFavored
import infra.user.UserRepository
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

    @Resource("/favored")
    class Favored(val parent: UserRes)
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
        get<UserRes.Favored> {
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavored(
                    user = user,
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
    ): Page<UserOutlineDto> {
        user.shouldBeAtLeast(User.Role.Admin)
        return userRepo.listUser(
            page = page,
            pageSize = pageSize,
            role = role,
        ).map {
            UserOutlineDto(
                id = it.id.toHexString(),
                email = it.email,
                username = it.username,
                role = it.role,
                createdAt = it.createdAt.epochSeconds,
            )
        }
    }

    @Serializable
    data class UserFavoredList(
        val web: List<UserFavored>,
        val wenku: List<UserFavored>,
    )

    suspend fun listFavored(user: AuthenticatedUser): UserFavoredList {
        val user = userRepo.getById(user.id)!!
        return UserFavoredList(
            web = user.favoredWeb,
            wenku = user.favoredWenku,
        )
    }
}
