package ch.cyberduck.core.ctera;

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

import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.dav.DAVTouchFeature;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.InvalidFilenameException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;

import static ch.cyberduck.core.ctera.CteraAttributesFinderFeature.*;

public class CteraTouchFeature extends DAVTouchFeature {

    private static final Logger log = LogManager.getLogger(CteraTouchFeature.class);

    public CteraTouchFeature(final CteraSession session) {
        super(new CteraWriteFeature(session));
    }

    @Override
    public void preflight(final Path workdir, final String filename) throws BackgroundException {
        if(!validate(filename)) {
            throw new InvalidFilenameException(MessageFormat.format(LocaleFactory.localizedString("Cannot create {0}", "Error"), filename));
        }

        // File/directory creation summary:
        // - Directories with ctera:writepermission but no ctera:createdirectoriespermission allow for file creation only.
        // - Directories with ctera:createdirectoriespermission but no ctera:writepermission allow for directory and file creation.
        // - Directories with only ctera:readpermission do not allow for file nor directory creation, for listing only.
        // In other words:
        // - file creation is allowed if either ctera:createdirectoriespermission or ctera:writepermission is set or both are set
        // - directory creation is allowed if ctera:createdirectoriespermission is set.

        // ctera:createdirectoriespermission or ctera:writepermission
        try {
            assumeRole(workdir, WRITEPERMISSION);
        }
        catch(AccessDeniedException e) {
            // ignore and try second option
            assumeRole(workdir, CREATEDIRECTORIESPERMISSION);
        }
    }

    public static boolean validate(final String filename) {
        if(StringUtils.containsAny(filename, '\\', '<', '>', ':', '"', '|', '?', '*', '/')) {
            log.warn("Validation failed for target name {}", filename);
            return false;
        }
        return true;
    }
}
