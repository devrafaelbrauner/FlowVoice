package dev.rafaelbrauner.flowvoice.shared.auth

data class AuthUser(
    val id: String,
    val email: String
)

class AuthException(message: String) : Exception(message)

interface AuthGateway {
    fun currentUser(): AuthUser?
    fun signIn(user: AuthUser)
    fun signOut()
    val isSignedIn: Boolean
        get() = currentUser() != null
}

class InMemoryAuthGateway : AuthGateway {
    private var user: AuthUser? = null
    override fun currentUser(): AuthUser? = user
    override fun signIn(user: AuthUser) {
        require(user.email.isNotBlank()) { "email must not be blank" }
        this.user = user
    }
    override fun signOut() {
        user = null
    }
}
