package mara.server.domain.user

import mara.server.auth.KakaoApiClient
import mara.server.auth.google.GoogleApiClient
import mara.server.auth.jwt.JwtProvider
import mara.server.auth.security.getCurrentLoginUserId
import mara.server.util.logger
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val client: KakaoApiClient,
    private val googleClient: GoogleApiClient,
) {

    val log = logger()

    fun getCurrentLoginUser(): User = userRepository.findById(getCurrentLoginUserId()).orElseThrow()

    fun getCurrentUserInfo() = UserResponseDto(getCurrentLoginUser())

    fun createUser(userRequest: UserRequest): Long {
        val user = User(
            name = userRequest.name,
            kaKaoId = userRequest.kaKaoId,
            password = passwordEncoder.encode(userRequest.name),
            googleEmail = userRequest.googleEmail,
        )
        return userRepository.save(user).userId
    }

    fun kaKaoLogin(authorizedCode: String): JwtDto {
        // 리다이랙트 url 환경 따라 다르게 전달하기 위한 구분 값
        val status = ""
        val accessToken = client.requestAccessToken(authorizedCode, status)
        val infoResponse = client.requestOauthInfo(accessToken)
        log.info("kakaoId ? " + infoResponse.id)
        val user = userRepository.findByKaKaoId(infoResponse.id)

        val userName = infoResponse.id

        // password 는 서비스에서 사용X, Security 설정을 위해 넣어준 값
        val password = userName.toString()

        if (user != null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userName, password)

            val refreshToken = UUID.randomUUID().toString()
            return JwtDto(jwtProvider.generateToken(user), refreshToken)
        }

        return JwtDto(null, null)
    }

    fun googleLogin(authorizedCode: String): JwtDto {
        val status = ""
        val accessToken = googleClient.requestAccessToken(authorizedCode, status)
        val infoResponse = googleClient.requestOauthInfo(accessToken)

        log.info(infoResponse.email)

        val user = userRepository.findByGoogleEmail(infoResponse.email)

        val userName = infoResponse.email

        if (user != null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userName, userName)

            val refreshToken = UUID.randomUUID().toString()
            return JwtDto(jwtProvider.generateToken(user), refreshToken)
        }

        return JwtDto(null, null)
    }
}
