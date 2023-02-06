package ch.cyberduck.core.nextcloud;

/*
 * Copyright (c) 2002-2022 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.AlphanumericRandomStringService;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DescriptiveUrl;
import ch.cyberduck.core.DisabledPasswordCallback;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.dav.DAVDeleteFeature;
import ch.cyberduck.core.dav.DAVDirectoryFeature;
import ch.cyberduck.core.dav.DAVTouchFeature;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.PromptUrlProvider;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class NextcloudShareProviderTest extends AbstractNextcloudTest {

    @Test
    public void testIsSupported() {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        assertTrue(new NextcloudShareProvider(session).isSupported(home, PromptUrlProvider.Type.download));
        assertTrue(new NextcloudShareProvider(session).isSupported(home, PromptUrlProvider.Type.upload));
    }

    @Test
    public void testToDownloadUrlNoPassword() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path file = new DAVTouchFeature(session).touch(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file)), new TransferStatus());
        final DescriptiveUrl url = new NextcloudShareProvider(session).toDownloadUrl(file, null, new DisabledPasswordCallback());
        assertNotSame(DescriptiveUrl.EMPTY, url);
        new DAVDeleteFeature(session).delete(Collections.singletonList(file), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testToUploadUrlNoPassword() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final DescriptiveUrl url = new NextcloudShareProvider(session).toUploadUrl(folder, null, new DisabledPasswordCallback());
        assertNotSame(DescriptiveUrl.EMPTY, url);
        new DAVDeleteFeature(session).delete(Collections.singletonList(folder), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testToDownloadUrlPasswordTooShort() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        try {
            new NextcloudShareProvider(session).toDownloadUrl(folder, null, new DisabledPasswordCallback() {
                @Override
                public Credentials prompt(final Host bookmark, final String title, final String reason, final LoginOptions options) {
                    return new Credentials(null, new AlphanumericRandomStringService(5).random());
                }
            });
            fail();
        }
        catch(AccessDeniedException e) {
            assertEquals("Forbidden. Password needs to be at least 10 characters long. Please contact your web hosting service provider for assistance.", e.getDetail());
        }
        new DAVDeleteFeature(session).delete(Collections.singletonList(folder), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testToDownloadUrlPassword() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path file = new DAVTouchFeature(session).touch(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file)), new TransferStatus());
        final DescriptiveUrl url = new NextcloudShareProvider(session).toDownloadUrl(file, null, new DisabledPasswordCallback() {
            @Override
            public Credentials prompt(final Host bookmark, final String title, final String reason, final LoginOptions options) {
                return new Credentials(null, new AlphanumericRandomStringService(10).random());
            }
        });
        assertNotSame(DescriptiveUrl.EMPTY, url);
        new DAVDeleteFeature(session).delete(Collections.singletonList(file), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testToDownloadShareRoot() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        try {
            new NextcloudShareProvider(session).toDownloadUrl(home, null, new DisabledPasswordCallback() {
                @Override
                public Credentials prompt(final Host bookmark, final String title, final String reason, final LoginOptions options) {
                    return new Credentials(null, new AlphanumericRandomStringService(10).random());
                }
            });
            fail();
        }
        catch(AccessDeniedException e) {
            assertEquals("Forbidden. You cannot share your root folder. Please contact your web hosting service provider for assistance.", e.getDetail());
        }
    }

    @Test
    public void testToUploadUrl() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final DescriptiveUrl url = new NextcloudShareProvider(session).toUploadUrl(folder, null, new DisabledPasswordCallback() {
            @Override
            public Credentials prompt(final Host bookmark, final String title, final String reason, final LoginOptions options) {
                return new Credentials(null, new AlphanumericRandomStringService(10).random());
            }
        });
        assertNotSame(DescriptiveUrl.EMPTY, url);
        new DAVDeleteFeature(session).delete(Collections.singletonList(folder), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testToUploadUrlPasswordTooShort() throws Exception {
        final Path home = new NextcloudHomeFeature(session.getHost()).find();
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        try {
            new NextcloudShareProvider(session).toUploadUrl(folder, null, new DisabledPasswordCallback() {
                @Override
                public Credentials prompt(final Host bookmark, final String title, final String reason, final LoginOptions options) {
                    return new Credentials(null, new AlphanumericRandomStringService(5).random());
                }
            });
            fail();
        }
        catch(AccessDeniedException e) {
            assertEquals("Forbidden. Password needs to be at least 10 characters long. Please contact your web hosting service provider for assistance.", e.getDetail());
        }
        new DAVDeleteFeature(session).delete(Collections.singletonList(folder), new DisabledPasswordCallback(), new Delete.DisabledCallback());
    }
}