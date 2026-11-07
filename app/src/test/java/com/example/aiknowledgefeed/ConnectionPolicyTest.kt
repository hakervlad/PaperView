package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.models.Settings
import com.example.aiknowledgefeed.backend.services.ConnectionPolicy
import org.junit.Test

class ConnectionPolicyTest {
    @Test fun permitsHttps() { ConnectionPolicy.validate(Settings("https://example.com/", "test-token"), false) }
    @Test fun permitsEmulatorDebugOnly() { ConnectionPolicy.validate(Settings("http://10.0.2.2:8000/", "test-token"), true) }
    @Test(expected = IllegalArgumentException::class) fun deniesReleaseHttp() { ConnectionPolicy.validate(Settings("http://10.0.2.2:8000/", "test-token"), false) }
    @Test(expected = IllegalArgumentException::class) fun deniesRemoteHttp() { ConnectionPolicy.validate(Settings("http://example.com/", "test-token"), true) }
    @Test(expected = IllegalArgumentException::class) fun deniesEmbeddedCredentials() { ConnectionPolicy.validate(Settings("https://user:secret@example.com/", "test-token"), false) }
    @Test(expected = IllegalArgumentException::class) fun deniesHeaderInjection() { ConnectionPolicy.validate(Settings("https://example.com/", "test\nheader"), false) }
    @Test(expected = IllegalArgumentException::class) fun deniesMissingAuthentication() { ConnectionPolicy.validate(Settings("https://example.com/", ""), false) }
}
