package io.shellify.app.presentation.add

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.shellify.app.core.engine.GeckoEngineManager
import io.shellify.app.core.iconpack.SimpleIconsManager
import io.shellify.app.core.iconpack.SimpleIconsState
import io.shellify.app.core.pwa.FaviconFetcher
import io.shellify.app.core.pwa.PwaAnalyzer
import io.shellify.app.core.security.PasswordManager
import io.shellify.app.core.theme.ThemeManager
import io.shellify.app.domain.model.EngineType
import io.shellify.app.domain.usecase.GetCategoriesUseCase
import io.shellify.app.domain.usecase.GetWebAppByIdUseCase
import io.shellify.app.domain.usecase.GetWebAppByNameUseCase
import io.shellify.app.domain.usecase.SaveWebAppUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelOnionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val getWebAppById = mockk<GetWebAppByIdUseCase>()
    private val getWebAppByName = mockk<GetWebAppByNameUseCase>()
    private val saveWebApp = mockk<SaveWebAppUseCase>(relaxed = true)
    private val getCategories = mockk<GetCategoriesUseCase>()
    private val analyzer = mockk<PwaAnalyzer>(relaxed = true)
    private val faviconFetcher = mockk<FaviconFetcher>(relaxed = true)
    private val geckoEngineManager = mockk<GeckoEngineManager>(relaxed = true)
    private val themeManager = mockk<ThemeManager>(relaxed = true)
    private val simpleIconsManager = mockk<SimpleIconsManager>(relaxed = true)
    private val passwordManager = mockk<PasswordManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { getCategories() } returns flowOf(emptyList())
        every { simpleIconsManager.state } returns MutableStateFlow(SimpleIconsState.NotImported)
        every { themeManager.defaultEngineType } returns MutableStateFlow(EngineType.SYSTEM_WEBVIEW)
        every { themeManager.globalNotificationsEnabled } returns MutableStateFlow(true)
        every { passwordManager.passwordHash } returns MutableStateFlow(null)
        // No duplicate name found by default — lets save() proceed past the name uniqueness check.
        coEvery { getWebAppByName(any()) } returns null
        // save() calls getWebAppById after persisting to load the saved entity.
        // Return null so the ViewModel falls back to buildApp().
        coEvery { getWebAppById(any()) } returns null
        // getString() returns "" for any resource ID by default (mockk relaxed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = AddViewModel(
        appId = 0L,
        getWebAppById = getWebAppById,
        getWebAppByName = getWebAppByName,
        saveWebApp = saveWebApp,
        getCategories = getCategories,
        analyzer = analyzer,
        faviconFetcher = faviconFetcher,
        geckoEngineManager = geckoEngineManager,
        themeManager = themeManager,
        simpleIconsManager = simpleIconsManager,
        passwordManager = passwordManager,
        context = context,
    )

    @Test
    fun `http onion url is accepted without urlError`() = runTest {
        val vm = newVm()
        vm.setName("DarkSite")
        vm.setUrl("http://abcdef1234567890.onion")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertNull("http .onion must not set urlError", vm.uiState.value.urlError)
    }

    @Test
    fun `bare onion url defaults to http for preview`() {
        val vm = newVm()
        vm.setName("DarkSite")
        vm.setUrl("abcdef1234567890.onion:8080/path")
        vm.run()

        assertNull(vm.uiState.value.urlError)
        assertEquals("http://abcdef1234567890.onion:8080/path", vm.uiState.value.previewUrl)
    }

    /**
     * A3: Bare *.onion (no scheme) should be normalized to http:// — NOT https://.
     * Onion services rarely have valid HTTPS certificates; defaulting to http keeps them working.
     */
    @Test
    fun `bare onion urls with nested links default to http`() {
        val urls = listOf(
            "abcdef1234567890.onion/go?next=https://example.com/Path",
            "abcdef1234567890.onion/go/http://example.com/Path",
            "abcdef1234567890.onion:8080/go?next=http://other.onion/Path#Return",
        )
        for (url in urls) {
            val vm = newVm()
            vm.setName("DarkSite")
            vm.setUrl(url)
            assertTrue(vm.isOnionUrl(url))
            vm.run()
            assertNull(vm.uiState.value.urlError)
            assertEquals("http://$url", vm.uiState.value.previewUrl)
        }
    }

    @Test
    fun `bare onion hostname is classified as onion url`() {
        val vm = newVm()
        assertTrue(vm.isOnionUrl("abcdef1234567890.onion"))
        assertTrue(vm.isOnionUrl("abcdef1234567890.onion/path"))
        assertTrue(vm.isOnionUrl("http://abcdef1234567890.onion"))
        assertTrue(vm.isOnionUrl("https://abcdef1234567890.onion"))
        // Non-onion must return false
        assertFalse(vm.isOnionUrl("https://example.com"))
        assertFalse(vm.isOnionUrl("http://example.com"))
    }

    /**
     * A4: https://host.onion (explicit https) should be accepted without error.
     */
    @Test
    fun `https onion url is accepted without urlError`() = runTest {
        val vm = newVm()
        vm.setName("SecureOnion")
        vm.setUrl("https://abcdef1234567890.onion")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertNull("https .onion must not set urlError", vm.uiState.value.urlError)
    }
}
