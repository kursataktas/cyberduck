package ch.cyberduck.ui.cocoa;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.core.*;
import ch.cyberduck.core.editor.WatchEditor;
import ch.cyberduck.core.features.Touch;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.local.FileDescriptor;
import ch.cyberduck.core.local.FileDescriptorFactory;
import ch.cyberduck.core.local.IconServiceFactory;
import ch.cyberduck.core.transfer.copy.CopyTransfer;
import ch.cyberduck.core.transfer.download.DownloadTransfer;
import ch.cyberduck.core.transfer.upload.UploadTransfer;
import ch.cyberduck.ui.action.SessionListWorker;
import ch.cyberduck.ui.cocoa.application.NSApplication;
import ch.cyberduck.ui.cocoa.application.NSDraggingInfo;
import ch.cyberduck.ui.cocoa.application.NSDraggingSource;
import ch.cyberduck.ui.cocoa.application.NSEvent;
import ch.cyberduck.ui.cocoa.application.NSImage;
import ch.cyberduck.ui.cocoa.application.NSPasteboard;
import ch.cyberduck.ui.cocoa.application.NSTableView;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSAttributedString;
import ch.cyberduck.ui.cocoa.foundation.NSFileManager;
import ch.cyberduck.ui.cocoa.foundation.NSMutableArray;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.foundation.NSString;
import ch.cyberduck.ui.cocoa.foundation.NSURL;
import ch.cyberduck.ui.cocoa.threading.WorkerBackgroundAction;
import ch.cyberduck.ui.pasteboard.PathPasteboard;
import ch.cyberduck.ui.pasteboard.PathPasteboardFactory;
import ch.cyberduck.ui.resources.IconCacheFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Rococoa;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSPoint;
import org.rococoa.cocoa.foundation.NSRect;
import org.rococoa.cocoa.foundation.NSSize;
import org.rococoa.cocoa.foundation.NSUInteger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version $Id$
 */
public abstract class BrowserTableDataSource extends ProxyController implements NSDraggingSource {
    private static final Logger log = Logger.getLogger(BrowserTableDataSource.class);

    public enum Columns {
        ICON,
        FILENAME,
        SIZE,
        MODIFIED,
        OWNER,
        GROUP,
        PERMISSIONS,
        KIND,
        EXTENSION,
        REGION
    }

    private FileDescriptor descriptor = FileDescriptorFactory.get();

    protected BrowserController controller;

    public BrowserTableDataSource(final BrowserController controller) {
        this.controller = controller;
    }

    /**
     * Clear the view cache
     */
    protected void clear() {
        tableViewCache.clear();
    }

    @Override
    protected void invalidate() {
        this.clear();
        super.invalidate();
    }

    /**
     * Must be efficient; called very frequently by the table view
     *
     * @param directory The directory to fetch the children from
     * @return The cached or newly fetched file listing of the directory
     */
    protected AttributedList<Path> list(final Path directory) {
        final Cache cache = controller.getSession().cache();
        if(!cache.isCached(directory.getReference())) {
            // Reloading a working directory that is not cached yet would cause the interface to freeze;
            // Delay until path is cached in the background
            controller.background(new WorkerBackgroundAction(controller,
                    new SessionListWorker(controller.getSession(), cache, directory, new DisabledListProgressListener()) {
                        @Override
                        public void cleanup(final AttributedList<Path> list) {
                            tableViewCache.clear();
                            controller.reloadData(true, true);
                        }
                    })
            );
        }
        return this.get(directory);
    }

    protected AttributedList<Path> get(final Path directory) {
        final Cache cache = controller.getSession().cache();
        return cache.get(directory.getReference()).filter(controller.getComparator(), controller.getFileFilter());
    }

    public int indexOf(NSTableView view, PathReference reference) {
        return this.get(controller.workdir()).indexOf(reference);
    }

    protected void setObjectValueForItem(final Path item, final NSObject value, final String identifier) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set new value %s for item %s", value, item));
        }
        if(identifier.equals(Columns.FILENAME.name())) {
            if(StringUtils.isNotBlank(value.toString()) && !item.getName().equals(value.toString())) {
                final Path renamed = new Path(
                        item.getParent(), value.toString(), item.attributes().getType());
                controller.renamePath(item, renamed);
            }
        }
    }

    protected NSImage iconForPath(final Path item) {
        if(item.attributes().isVolume()) {
            return IconCacheFactory.<NSImage>get().volumeIcon(controller.getSession().getHost().getProtocol(), 16);
        }
        return IconCacheFactory.<NSImage>get().fileIcon(item, 16);
    }

    /**
     * Second cache because it is expensive to create proxy instances
     */
    private AttributeCache<Path> tableViewCache = new AttributeCache<Path>(
            Preferences.instance().getInteger("browser.model.cache.size")
    );

    protected NSObject objectValueForItem(final Path item, final String identifier) {
        if(null == item) {
            return null;
        }
        if(log.isTraceEnabled()) {
            log.trace("objectValueForItem:" + item.getAbsolute());
        }
        final NSObject cached = tableViewCache.get(item, identifier);
        if(null == cached) {
            if(identifier.equals(Columns.ICON.name())) {
                return tableViewCache.put(item, identifier, this.iconForPath(item));
            }
            if(identifier.equals(Columns.FILENAME.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        item.getName(),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.SIZE.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        SizeFormatterFactory.get().format(item.attributes().getSize()),
                        TableCellAttributes.browserFontRightAlignment()));
            }
            if(identifier.equals(Columns.MODIFIED.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        UserDateFormatterFactory.get().getShortFormat(item.attributes().getModificationDate(),
                                Preferences.instance().getBoolean("browser.date.natural")),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.OWNER.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        StringUtils.isBlank(item.attributes().getOwner()) ? LocaleFactory.localizedString("Unknown") : item.attributes().getOwner(),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.GROUP.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        StringUtils.isBlank(item.attributes().getGroup()) ? LocaleFactory.localizedString("Unknown") : item.attributes().getGroup(),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.PERMISSIONS.name())) {
                Permission permission = item.attributes().getPermission();
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        permission.toString(),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.KIND.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        descriptor.getKind(item),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.EXTENSION.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        item.attributes().isFile() ? StringUtils.isNotBlank(item.getExtension()) ? item.getExtension() : LocaleFactory.localizedString("None") : LocaleFactory.localizedString("None"),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            if(identifier.equals(Columns.REGION.name())) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        StringUtils.isNotBlank(item.attributes().getRegion()) ? item.attributes().getRegion() : LocaleFactory.localizedString("Unknown"),
                        TableCellAttributes.browserFontLeftAlignment()));
            }
            throw new IllegalArgumentException(String.format("Unknown identifier %s", identifier));
        }
        return cached;
    }

    /**
     * Sets whether the use of modifier keys should have an effect on the type of operation performed.
     *
     * @return Always false
     * @see NSDraggingSource
     */
    @Override
    public boolean ignoreModifierKeysWhileDragging() {
        // If this method is not implemented or returns false, the user can tailor the drag operation by
        // holding down a modifier key during the drag.
        return false;
    }

    /**
     * @param local indicates that the candidate destination object (the window or view over which the dragged
     *              image is currently poised) is in the same application as the source, while a NO value indicates that
     *              the destination object is in a different application
     * @return A mask, created by combining the dragging operations listed in the NSDragOperation section of
     *         NSDraggingInfo protocol reference using the C bitwise OR operator.If the source does not permit
     *         any dragging operations, it should return NSDragOperationNone.
     * @see NSDraggingSource
     */
    @Override
    public NSUInteger draggingSourceOperationMaskForLocal(boolean local) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Request dragging source operation mask for %s", local));
        }
        if(local) {
            // Move or copy within the browser
            return new NSUInteger(NSDraggingInfo.NSDragOperationMove.intValue() | NSDraggingInfo.NSDragOperationCopy.intValue());
        }
        // Copy to a thirdparty application or drag to trash to delete
        return new NSUInteger(NSDraggingInfo.NSDragOperationCopy.intValue() | NSDraggingInfo.NSDragOperationDelete.intValue());
    }

    /**
     * @param view        Table
     * @param destination A directory or null to mount an URL
     * @param info        Dragging pasteboard
     * @return True if accepted
     */
    public boolean acceptDrop(NSTableView view, final Path destination, NSDraggingInfo info) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Accept drop for destination %s", destination));
        }
        if(info.draggingPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.URLPboardType)) != null) {
            NSObject o = info.draggingPasteboard().propertyListForType(NSPasteboard.URLPboardType);
            // Mount .webloc URLs dragged to browser window
            if(o != null) {
                final NSArray elements = Rococoa.cast(o, NSArray.class);
                for(int i = 0; i < elements.count().intValue(); i++) {
                    if(ProtocolFactory.isURL(elements.objectAtIndex(new NSUInteger(i)).toString())) {
                        controller.mount(HostParser.parse(elements.objectAtIndex(new NSUInteger(i)).toString()));
                        return true;
                    }
                }
            }
        }
        if(controller.isMounted()) {
            if(info.draggingPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
                NSObject o = info.draggingPasteboard().propertyListForType(NSPasteboard.FilenamesPboardType);
                // A file drag has been received by another application; upload to the dragged directory
                if(o != null) {
                    final NSArray elements = Rococoa.cast(o, NSArray.class);
                    final Session session = controller.getTransferSession();
                    final List<Path> roots = new Collection<Path>();
                    for(int i = 0; i < elements.count().intValue(); i++) {
                        Path p = new Path(destination, LocalFactory.createLocal(elements.objectAtIndex(new NSUInteger(i)).toString()));
                        roots.add(p);
                    }
                    controller.transfer(new UploadTransfer(session, roots));
                    return true;
                }
                return false;
            }
            final List<PathPasteboard> pasteboards = PathPasteboardFactory.allPasteboards();
            for(PathPasteboard pasteboard : pasteboards) {
                // A file dragged within the browser has been received
                if(pasteboard.isEmpty()) {
                    continue;
                }
                if(info.draggingSourceOperationMask().intValue() == NSDraggingInfo.NSDragOperationCopy.intValue()
                        || !pasteboard.getSession().equals(controller.getSession())) {
                    // Drag to browser windows with different session or explicit copy requested by user.
                    final Map<Path, Path> files = new HashMap<Path, Path>();
                    for(Path file : pasteboard) {
                        files.put(file, new Path(destination, file.getName(), file.attributes().getType()));
                    }
                    controller.transfer(new CopyTransfer(pasteboard.getSession(), controller.getSession(), files));
                }
                else {
                    // The file should be renamed
                    final Map<Path, Path> files = new HashMap<Path, Path>();
                    for(Path next : pasteboard) {
                        Path renamed = new Path(
                                destination, next.getName(), next.attributes().getType());
                        files.put(next, renamed);
                    }
                    controller.renamePaths(files);
                }
                pasteboard.clear();
            }
            return true;
        }
        return false;
    }

    /**
     * @param view        Table
     * @param destination A directory or null to mount an URL
     * @param row         Index
     * @param info        Dragging pasteboard
     * @return Drag operation
     */
    public NSUInteger validateDrop(NSTableView view, Path destination, NSInteger row, NSDraggingInfo info) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Validate drop for destination %s", destination));
        }
        if(info.draggingPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.URLPboardType)) != null) {
            // Dragging URLs to mount new session
            NSObject o = info.draggingPasteboard().propertyListForType(NSPasteboard.URLPboardType);
            if(o != null) {
                NSArray elements = Rococoa.cast(o, NSArray.class);
                for(int i = 0; i < elements.count().intValue(); i++) {
                    // Validate if .webloc URLs dragged to browser window have a known protocol
                    if(ProtocolFactory.isURL(elements.objectAtIndex(new NSUInteger(i)).toString())) {
                        // Passing a value of –1 for row, and NSTableViewDropOn as the operation causes the
                        // entire table view to be highlighted rather than a specific row.
                        view.setDropRow(new NSInteger(-1), NSTableView.NSTableViewDropOn);
                        return NSDraggingInfo.NSDragOperationCopy;
                    }
                    else {
                        log.warn(String.format("Protocol not supported for URL %s", elements.objectAtIndex(new NSUInteger(i)).toString()));
                    }
                }
            }
            else {
                log.warn("URL dragging pasteboard is empty.");
            }
        }
        if(controller.isMounted()) {
            if(null == destination) {
                log.warn("Dragging destination is null.");
                return NSDraggingInfo.NSDragOperationNone;
            }
            final Touch feature = controller.getSession().getFeature(Touch.class);
            if(!feature.isSupported(destination)) {
                // Target file system does not support creating files. Creating files is not supported
                // for example in root of cloud storage accounts.
                return NSDraggingInfo.NSDragOperationNone;
            }
            // Files dragged form other application
            if(info.draggingPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
                this.setDropRowAndDropOperation(view, destination, row);
                return NSDraggingInfo.NSDragOperationCopy;
            }
            // Files dragged from browser
            for(Path next : controller.getPasteboard()) {
                if(destination.equals(next)) {
                    // Do not allow dragging onto myself
                    return NSDraggingInfo.NSDragOperationNone;
                }
                if(next.attributes().isDirectory() && destination.isChild(next)) {
                    // Do not allow dragging a directory into its own containing items
                    return NSDraggingInfo.NSDragOperationNone;
                }
                if(next.attributes().isFile() && next.getParent().equals(destination)) {
                    // Moving a file to the same destination makes no sense
                    return NSDraggingInfo.NSDragOperationNone;
                }
            }
            if(log.isDebugEnabled()) {
                log.debug(String.format("Drag operation mas is %d", info.draggingSourceOperationMask().intValue()));
            }
            this.setDropRowAndDropOperation(view, destination, row);
            final List<PathPasteboard> pasteboards = PathPasteboardFactory.allPasteboards();
            for(PathPasteboard pasteboard : pasteboards) {
                if(pasteboard.isEmpty()) {
                    continue;
                }
                if(pasteboard.getSession().equals(controller.getSession())) {
                    if(info.draggingSourceOperationMask().intValue() == NSDraggingInfo.NSDragOperationCopy.intValue()) {
                        // Explicit copy requested if drag operation is already NSDragOperationCopy. User is pressing the option key.
                        return NSDraggingInfo.NSDragOperationCopy;
                    }
                    // Defaulting to move for same session
                    return NSDraggingInfo.NSDragOperationMove;
                }
                else {
                    // If copying between sessions is supported
                    return NSDraggingInfo.NSDragOperationCopy;
                }
            }
        }
        return NSDraggingInfo.NSDragOperationNone;
    }

    private void setDropRowAndDropOperation(NSTableView view, Path destination, NSInteger row) {
        if(destination.equals(controller.workdir())) {
            log.debug("setDropRowAndDropOperation:-1");
            // Passing a value of –1 for row, and NSTableViewDropOn as the operation causes the
            // entire table view to be highlighted rather than a specific row.
            view.setDropRow(new NSInteger(-1), NSTableView.NSTableViewDropOn);
        }
        else if(destination.attributes().isDirectory()) {
            log.debug("setDropRowAndDropOperation:" + row.intValue());
            view.setDropRow(row, NSTableView.NSTableViewDropOn);
        }
    }

    public boolean writeItemsToPasteBoard(NSTableView view, NSArray items, NSPasteboard pboard) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Write items to pasteboard %s", pboard));
        }
        if(controller.isMounted()) {
            if(items.count().intValue() > 0) {
                // The fileTypes argument is the list of fileTypes being promised.
                // The array elements can consist of file extensions and HFS types encoded
                // with the NSHFSFileTypes method fileTypeForHFSTypeCode. If promising a directory
                // of files, only include the top directory in the array.
                final NSMutableArray fileTypes = NSMutableArray.array();
                final PathPasteboard pasteboard = controller.getPasteboard();
                for(int i = 0; i < items.count().intValue(); i++) {
                    final Path path = controller.lookup(new NSObjectPathReference(items.objectAtIndex(new NSUInteger(i))));
                    if(path.attributes().isFile()) {
                        if(StringUtils.isNotEmpty(path.getExtension())) {
                            fileTypes.addObject(NSString.stringWithString(path.getExtension()));
                        }
                        else {
                            fileTypes.addObject(NSString.stringWithString(NSFileManager.NSFileTypeRegular));
                        }
                    }
                    else if(path.attributes().isDirectory()) {
                        fileTypes.addObject(NSString.stringWithString("'fldr'")); //NSFileTypeForHFSTypeCode('fldr')
                    }
                    else {
                        fileTypes.addObject(NSString.stringWithString(NSFileManager.NSFileTypeUnknown));
                    }
                    // Writing data for private use when the item gets dragged to the transfer queue.
                    pasteboard.add(path);
                }
                NSEvent event = NSApplication.sharedApplication().currentEvent();
                if(event != null) {
                    NSPoint dragPosition = view.convertPoint_fromView(event.locationInWindow(), null);
                    NSRect imageRect = new NSRect(new NSPoint(dragPosition.x.doubleValue() - 16, dragPosition.y.doubleValue() - 16), new NSSize(32, 32));
                    view.dragPromisedFilesOfTypes(fileTypes, imageRect, this.id(), true, event);
                    // @see http://www.cocoabuilder.com/archive/message/cocoa/2003/5/15/81424
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void draggedImage_beganAt(NSImage image, NSPoint point) {
        if(log.isTraceEnabled()) {
            log.trace("draggedImage_beganAt:" + point);
        }
    }

    /**
     * See http://www.cocoabuilder.com/archive/message/2005/10/5/118857
     */
    @Override
    public void draggedImage_endedAt_operation(NSImage image, NSPoint point, NSUInteger operation) {
        if(log.isTraceEnabled()) {
            log.trace("draggedImage_endedAt_operation:" + operation);
        }
        final PathPasteboard pasteboard = controller.getPasteboard();
        if(NSDraggingInfo.NSDragOperationDelete.intValue() == operation.intValue()) {
            controller.deletePaths(pasteboard);
        }
        pasteboard.clear();
    }

    @Override
    public void draggedImage_movedTo(NSImage image, NSPoint point) {
        if(log.isTraceEnabled()) {
            log.trace("draggedImage_movedTo:" + point);
        }
    }

    /**
     * @return the names (not full paths) of the files that the receiver promises to create at dropDestination.
     *         This method is invoked when the drop has been accepted by the destination and the destination, in the case of another
     *         Cocoa application, invokes the NSDraggingInfo method namesOfPromisedFilesDroppedAtDestination. For long operations,
     *         you can cache dropDestination and defer the creation of the files until the finishedDraggingImage method to avoid
     *         blocking the destination application.
     */
    @Override
    public NSArray namesOfPromisedFilesDroppedAtDestination(final NSURL url) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Return names of promised files dropped at %s", url));
        }
        NSMutableArray promisedDragNames = NSMutableArray.array();
        if(null != url) {
            final Local destination = LocalFactory.createLocal(url.path());
            final PathPasteboard pasteboard = controller.getPasteboard();
            for(Path p : pasteboard) {
                final Local local = LocalFactory.createLocal(destination, p.getName());
                p.setLocal(local);
                // Add to returned path names
                promisedDragNames.addObject(NSString.stringWithString(local.getName()));
            }
            if(pasteboard.size() == 1) {
                final Local file = pasteboard.get(0).getLocal();
                if(pasteboard.get(0).attributes().isFile()) {
                    file.touch();
                    IconServiceFactory.get().set(file, 0);
                }
                if(pasteboard.get(0).attributes().isDirectory()) {
                    file.mkdir();
                }
            }
            // kTemporaryFolderType
            final boolean dock = destination.equals(LocalFactory.createLocal("~/Library/Caches/TemporaryItems"));
            if(dock) {
                for(Path p : pasteboard) {
                    // Drag to application icon in dock.
                    WatchEditor editor = new WatchEditor(controller, controller.getSession(), null, p);
                    try {
                        // download
                        editor.watch();
                    }
                    catch(IOException e) {
                        log.error(e.getMessage());
                    }
                }
            }
            else {
                final DownloadTransfer transfer = new DownloadTransfer(controller.getTransferSession(),
                        pasteboard);
                controller.transfer(transfer, Collections.<Path>emptyList());
            }
            pasteboard.clear();
        }
        // Filenames
        return promisedDragNames;
    }
}
