package api

import api.plugins.authenticateDb
import api.plugins.shouldBeAtLeast
import api.plugins.user
import infra.common.Page
import infra.user.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
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
        val role: UserRole,
    )

    @Resource("/{id}")
    class Id(val parent: UserRes, val id: String) {
        @Resource("/role")
        class Role(val parent: Id)
    }

    @Resource("/favored")
    class Favored(val parent: UserRes)
}

fun Route.routeUser() {
    val service by inject<UserApi>()

    authenticateDb {
        get<UserRes.List> { loc ->
            val user = call.user()
            call.tryRespond {
                service.listUser(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    role = loc.role,
                )
            }
        }

        put<UserRes.Id.Role> { loc ->
            @Serializable
            class Body(val role: UserRole)
            val body = call.receive<Body>()
            val user = call.user()
            call.tryRespond {
                service.updateRole(
                    user = user, userId = loc.parent.id, role = body.role
                )
            }
        }

        get<UserRes.Favored> {
            val user = call.user()
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
    private val userFavoredRepo: UserFavoredRepository,
) {
    @Serializable
    data class UserOutlineDto(
        val id: String,
        val email: String,
        val username: String,
        val role: UserRole,
        val createdAt: Long,
    )

    suspend fun listUser(
        user: User,
        page: Int,
        pageSize: Int,
        role: UserRole,
    ): Page<UserOutlineDto> {
        user.shouldBeAtLeast(UserRole.Admin)
        return userRepo.listUser(
            page = page,
            pageSize = pageSize,
            role = role,
        ).map {
            UserOutlineDto(
                id = it.id,
                email = it.email,
                username = it.username,
                role = it.role,
                createdAt = it.createdAt.epochSeconds,
            )
        }
    }

    suspend fun updateRole(
        user: User,
        userId: String,
        role: UserRole,
    ) {
        user.shouldBeAtLeast(UserRole.Admin)
        userRepo.updateRole(
            userId = userId,
            role = role
        )
    }

    suspend fun listFavored(user: User): UserFavoredList {
        return userFavoredRepo.getFavoredList(user.id)
            ?: throwNotFound("用户不存在")
    }
}
