package ch.cyberduck.core.onedrive;

/*
 * Copyright (c) 2002-2020 iterate GmbH. All rights reserved.
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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Cache;
import ch.cyberduck.core.ListProgressListener;
import ch.cyberduck.core.ListService;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.IdProvider;
import ch.cyberduck.core.onedrive.features.sharepoint.SiteDrivesListService;
import ch.cyberduck.core.onedrive.features.sharepoint.SitesListService;

import static ch.cyberduck.core.onedrive.SharepointListService.*;

public abstract class AbstractSharepointListService implements ListService {

    private final AbstractSharepointSession session;
    private final IdProvider idProvider;

    public AbstractSharepointListService(final AbstractSharepointSession session, final IdProvider idProvider) {
        this.session = session;
        this.idProvider = idProvider;
    }

    public AbstractSharepointSession getSession() {
        return session;
    }

    public IdProvider getIdProvider() {
        return idProvider;
    }

    @Override
    public final AttributedList<Path> list(final Path directory, final ListProgressListener listener) throws BackgroundException {
        if(directory.isRoot()) {
            return getRoot(directory, listener);
        }

        final ProcessListResult result = new ProcessListResult();
        if(processList(directory, listener, result)) {
            return result.children;
        }

        if(SITES_ID.equals(directory.attributes().getVersionId())) {
            return new SitesListService(session).list(directory, listener);
        }
        else if(DRIVES_ID.equals(directory.attributes().getVersionId())) {
            return new SiteDrivesListService(session).list(directory, listener);
        }
        else if(SITES_ID.equals(directory.getParent().attributes().getVersionId())) {
            return addSiteItems(directory, listener);
        }

        return new GraphItemListService(session).list(directory, listener);
    }

    @Override
    public ListService withCache(final Cache<Path> cache) {
        idProvider.withCache(cache);
        return this;
    }

    AttributedList<Path> addDefaultItems(final Path directory, final ListProgressListener listener) throws BackgroundException {
        final AttributedList<Path> list = new AttributedList<>();
        list.add(DEFAULT_NAME);
        list.add(GROUPS_NAME);
        list.add(SITES_NAME);
        listener.chunk(directory, list);
        return list;
    }

    AttributedList<Path> addSiteItems(final Path directory, final ListProgressListener listener) throws BackgroundException {
        final AttributedList<Path> list = new AttributedList<>();
        list.add(new Path(directory, DRIVES_NAME.getName(), DRIVES_NAME.getType(), DRIVES_NAME.attributes()));
        list.add(new Path(directory, SITES_NAME.getName(), SITES_NAME.getType(), SITES_NAME.attributes()));
        listener.chunk(directory, list);
        return list;
    }

    abstract AttributedList<Path> getRoot(final Path directory, final ListProgressListener listener) throws BackgroundException;

    boolean processList(final Path directory, final ListProgressListener listener, final ProcessListResult result) throws BackgroundException {
        return false;
    }

    public static class ProcessListResult {
        private AttributedList<Path> children;

        public ProcessListResult withChildren(final AttributedList<Path> value) {
            this.children = value;
            return this;
        }

        public boolean success() {
            return true;
        }
    }
}
