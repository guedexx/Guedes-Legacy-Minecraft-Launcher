package com.mojang.authlib.yggdrasil;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import net.minecraft.launcher.security.WindowsTokenProtector;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Multimap;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.HttpUserAuthentication;
import com.mojang.authlib.UserType;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.yggdrasil.request.RefreshRequest;
import com.mojang.authlib.yggdrasil.request.ValidateRequest;
import com.mojang.authlib.yggdrasil.response.RefreshResponse;
import com.mojang.authlib.yggdrasil.response.Response;
import com.mojang.authlib.yggdrasil.response.User;
import com.mojang.util.ProfileUtil;

import fr.litarvan.openauth.microsoft.AuthTokens;
import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticator;
import fr.litarvan.openauth.microsoft.model.response.MinecraftProfile;
import net.minecraft.launcher.Launcher;

public class YggdrasilUserAuthentication extends HttpUserAuthentication {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final String BASE_URL = "https://authserver.mojang.com/";

	private static final URL ROUTE_REFRESH = HttpAuthenticationService.constantURL(BASE_URL + "refresh");
	private static final URL ROUTE_VALIDATE = HttpAuthenticationService.constantURL(BASE_URL + "validate");

	private static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
	private static final String STORAGE_KEY_REFRESH_TOKEN = "refreshToken";

	private final Agent agent;

	private GameProfile[] profiles;

	private String accessToken;
	private String minecraftToken;
	private String refreshToken;

	private boolean isOnline;

	public YggdrasilUserAuthentication(YggdrasilAuthenticationService authenticationService, Agent agent) {

		super(authenticationService);

		this.agent = agent;
	}

	public boolean canLogIn() {

		return !canPlayOnline();
	}

	public void logInWithMicrosoft()
			throws AuthenticationException, MicrosoftAuthenticationException {

		try {

			LOGGER.info("Starting Microsoft authentication");

			MicrosoftAuthResult response =
					new MicrosoftAuthenticator().loginWithWebview();

			if (response == null) {
				LOGGER.info("Microsoft authentication cancelled by user");
				throw new InvalidCredentialsException(
						"Microsoft authentication cancelled"
				);
			}

			MinecraftProfile profile = response.getProfile();

			setUsername(profile.getName());
			setUserid(profile.getId());

			setSelectedProfile(
					ProfileUtil.minecraftProfileToGameProfile(profile)
			);

			if (getSelectedProfile() != null) {
				setUserType(
						getSelectedProfile().isLegacy()
								? UserType.LEGACY
								: UserType.MOJANG
				);
			}

			this.isOnline = true;

			this.accessToken = response.getAccessToken();
			this.refreshToken = response.getRefreshToken();
			this.minecraftToken = response.getAccessToken();

			this.profiles = new GameProfile[] { getSelectedProfile() };

			getModifiableUserProperties().clear();

		} finally {

			Launcher.getCurrentInstance().starting = false;
		}
	}

	public void logIn() throws AuthenticationException, MicrosoftAuthenticationException {

		try {

			if (StringUtils.isNotBlank(this.refreshToken)) {

				LOGGER.info("Trying Microsoft login with saved refresh token");

				try {
					logInWithToken();
				} catch (Exception e) {
					LOGGER.warn("Saved Microsoft session could not be restored", e);

					this.accessToken = null;
					this.refreshToken = null;
					this.minecraftToken = null;
					this.isOnline = false;

					throw new InvalidCredentialsException("Saved Microsoft session expired");
				}

			} else {

				LOGGER.info("No saved Microsoft session");
				throw new InvalidCredentialsException("No saved Microsoft session");
			}

			if (getSelectedProfile() != null) {
				LOGGER.info("Logged as '" + getSelectedProfile().getName() + "'");
			}

		} finally {

			Launcher.getCurrentInstance().starting = false;
		}
	}

	protected void logInWithPassword() throws AuthenticationException, MicrosoftAuthenticationException {

		LOGGER.info("Logging in with Microsoft WebView");

		MicrosoftAuthResult response =
				new MicrosoftAuthenticator().loginWithWebview();

		if (response == null) {
			LOGGER.info("Microsoft authentication cancelled by user");
			throw new InvalidCredentialsException(
					"Microsoft authentication cancelled"
			);
		}

		MinecraftProfile profile = response.getProfile();

		setUsername(profile.getName());
		setSelectedProfile(ProfileUtil.minecraftProfileToGameProfile(profile));

		if (getSelectedProfile() != null) {
			setUserType(getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
		}

		setUserid(profile.getId());

		this.isOnline = true;

		this.accessToken = response.getAccessToken();

		// No OpenAuth 1.1.6 que estamos usando,
		// tratamos o token autenticado como o token da sessão.
		this.minecraftToken = response.getAccessToken();

		this.profiles = new GameProfile[] { getSelectedProfile() };

		getModifiableUserProperties().clear();
	}

	protected void updateUserProperties(User user) {

		if (user == null) return;

		if (user.getProperties() != null) getModifiableUserProperties().putAll((Multimap)user.getProperties());
	}

	protected void logInWithToken() throws AuthenticationException, MicrosoftAuthenticationException {

		if (StringUtils.isBlank(getUserID())) {

			if (StringUtils.isNotBlank(getUsername())) {

				setUserid(getUsername());

			} else {

				throw new InvalidCredentialsException("Invalid uuid & username");
			}
		}

		LOGGER.info("Logging in with access token");

		MicrosoftAuthResult result = new MicrosoftAuthenticator().loginWithRefreshToken(this.refreshToken);

		MinecraftProfile profile = result.getProfile();

		setSelectedProfile(ProfileUtil.minecraftProfileToGameProfile(profile));

		if (getSelectedProfile() != null) {

			setUserType(getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
		}

		User user = new User(getUserID());

		if (user != null && user.getId() != null) {

			setUserid(user.getId());

		} else {

			setUserid(getUsername());
		}

		this.isOnline = true;
		this.accessToken = result.getAccessToken();
		this.refreshToken = result.getRefreshToken();
		this.minecraftToken = result.getAccessToken();
		this.profiles = new GameProfile[] { getSelectedProfile() };

		getModifiableUserProperties().clear();

		updateUserProperties(user);
	}

	public void logOut() {

		super.logOut();

		this.accessToken = null;
		this.refreshToken = null;
		this.minecraftToken = null;
		this.profiles = null;
		this.isOnline = false;
	}

	public GameProfile[] getAvailableProfiles() {

		return this.profiles;
	}

	public boolean isLoggedIn() {

		return StringUtils.isNotBlank(this.minecraftToken);
	}

	public boolean canPlayOnline() {

		return (isLoggedIn() && getSelectedProfile() != null && this.isOnline);
	}

	public void selectGameProfile(GameProfile profile) throws AuthenticationException {

		if (!isLoggedIn()) throw new AuthenticationException("Cannot change game profile whilst not logged in");

		if (getSelectedProfile() != null) {

			throw new AuthenticationException("Cannot change game profile. You must log out and back in.");
		}

		if (profile == null || !ArrayUtils.contains((Object[])this.profiles, profile)) {

			throw new IllegalArgumentException("Invalid profile '" + profile + "'");
		}

		RefreshRequest request = new RefreshRequest(this, profile);

		RefreshResponse response = getAuthenticationService().<RefreshResponse>makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);

		if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {

			throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
		}

		this.isOnline = true;
		this.accessToken = response.getAccessToken();

		setSelectedProfile(response.getSelectedProfile());
	}

	public void loadFromStorage(Map<String, Object> credentials) {

		super.loadFromStorage(credentials);

		Object access = credentials.get(STORAGE_KEY_ACCESS_TOKEN);
		Object refresh = credentials.get(STORAGE_KEY_REFRESH_TOKEN);

		this.accessToken = access != null ? access.toString() : null;
		if (refresh != null) {

			try {
				this.refreshToken =
						WindowsTokenProtector.unprotect(refresh.toString());

			} catch (RuntimeException e) {

				LOGGER.warn(
						"Could not decrypt saved Microsoft refresh token",
						e
				);

				this.refreshToken = null;
			}

		} else {

			this.refreshToken = null;
		}
	}

	public Map<String, Object> saveForStorage() {

		Map<String, Object> result = super.saveForStorage();

		if (StringUtils.isNotBlank(this.refreshToken)) {
			try {
				result.put(
						STORAGE_KEY_REFRESH_TOKEN,
						WindowsTokenProtector.protect(this.refreshToken)
				);
			} catch (RuntimeException e) {
				LOGGER.error(
						"Could not securely store Microsoft refresh token",
						e
				);
			}
		}


		return result;
	}

	public String getAuthenticatedToken() {

		return this.minecraftToken;
	}

	public Agent getAgent() {

		return this.agent;
	}

	public String toString() {

		return "YggdrasilAuthenticationService{" +
				"agent=" + this.agent +
				", profiles=" + Arrays.toString((Object[])this.profiles) +
				", selectedProfile=" + getSelectedProfile() +
				", username='" + getUsername() + '\'' +
				", isLoggedIn=" + isLoggedIn() +
				", userType=" + getUserType() +
				", canPlayOnline=" + canPlayOnline() +
				'}';
	}

	public YggdrasilAuthenticationService getAuthenticationService() {

		return (YggdrasilAuthenticationService)super.getAuthenticationService();
	}
}
