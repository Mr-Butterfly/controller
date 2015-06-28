/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.cluster.raft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.cluster.raft.MockRaftActorContext.MockPayload;
import org.opendaylight.controller.cluster.raft.MockRaftActorContext.MockReplicatedLogEntry;
/**
*
*/
public class AbstractReplicatedLogImplTest {

    private MockAbstractReplicatedLogImpl replicatedLogImpl;

    @Before
    public void setUp() {
        replicatedLogImpl = new MockAbstractReplicatedLogImpl();
        // create a set of initial entries in the in-memory log
        replicatedLogImpl.append(new MockReplicatedLogEntry(1, 0, new MockPayload("A")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(1, 1, new MockPayload("B")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(1, 2, new MockPayload("C")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 3, new MockPayload("D")));

    }

    @Test
    public void testIndexOperations() {

        // check if the values returned are correct, with snapshotIndex = -1
        assertEquals("B", replicatedLogImpl.get(1).getData().toString());
        assertEquals("D", replicatedLogImpl.last().getData().toString());
        assertEquals(3, replicatedLogImpl.lastIndex());
        assertEquals(2, replicatedLogImpl.lastTerm());
        assertEquals(2, replicatedLogImpl.getFrom(2).size());
        assertEquals(4, replicatedLogImpl.size());
        assertTrue(replicatedLogImpl.isPresent(2));
        assertFalse(replicatedLogImpl.isPresent(4));
        assertFalse(replicatedLogImpl.isInSnapshot(2));

        // now create a snapshot of 3 entries, with 1 unapplied entry left in the log
        // It removes the entries which have made it to snapshot
        // and updates the snapshot index and term
        Map state = takeSnapshot(3);

        // check the values after the snapshot.
        // each index value passed in the test is the logical index (log entry index)
        // which gets mapped to the list's physical index
        assertEquals("D", replicatedLogImpl.get(3).getData().toString());
        assertEquals("D", replicatedLogImpl.last().getData().toString());
        assertNull(replicatedLogImpl.get(1));
        assertEquals(3, replicatedLogImpl.lastIndex());
        assertEquals(2, replicatedLogImpl.lastTerm());
        assertEquals(0, replicatedLogImpl.getFrom(2).size());
        assertEquals(1, replicatedLogImpl.size());
        assertFalse(replicatedLogImpl.isPresent(2));
        assertTrue(replicatedLogImpl.isPresent(3));
        assertFalse(replicatedLogImpl.isPresent(4));
        assertTrue(replicatedLogImpl.isInSnapshot(2));

        // append few more entries
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 4, new MockPayload("E")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 5, new MockPayload("F")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(3, 6, new MockPayload("G")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(3, 7, new MockPayload("H")));

        // check their values as well
        assertEquals(5, replicatedLogImpl.size());
        assertEquals("D", replicatedLogImpl.get(3).getData().toString());
        assertEquals("E", replicatedLogImpl.get(4).getData().toString());
        assertEquals("H", replicatedLogImpl.last().getData().toString());
        assertEquals(3, replicatedLogImpl.lastTerm());
        assertEquals(7, replicatedLogImpl.lastIndex());
        assertTrue(replicatedLogImpl.isPresent(7));
        assertFalse(replicatedLogImpl.isInSnapshot(7));
        assertEquals(1, replicatedLogImpl.getFrom(7).size());
        assertEquals(2, replicatedLogImpl.getFrom(6).size());

        // take a second snapshot with 5 entries with 0 unapplied entries left in the log
        state = takeSnapshot(5);

        assertEquals(0, replicatedLogImpl.size());
        assertNull(replicatedLogImpl.last());
        assertNull(replicatedLogImpl.get(7));
        assertNull(replicatedLogImpl.get(1));
        assertFalse(replicatedLogImpl.isPresent(7));
        assertTrue(replicatedLogImpl.isInSnapshot(7));
        assertEquals(0, replicatedLogImpl.getFrom(7).size());
        assertEquals(0, replicatedLogImpl.getFrom(6).size());

    }

    @Test
    public void testRemoveFrom() {

        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 4, new MockPayload("E", 2)));
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 5, new MockPayload("F", 3)));

        assertEquals("dataSize", 9, replicatedLogImpl.dataSize());

        long adjusted = replicatedLogImpl.removeFrom(4);
        assertEquals("removeFrom - adjusted", 4, adjusted);
        assertEquals("size", 4, replicatedLogImpl.size());
        assertEquals("dataSize", 4, replicatedLogImpl.dataSize());

        takeSnapshot(1);

        adjusted = replicatedLogImpl.removeFrom(2);
        assertEquals("removeFrom - adjusted", 1, adjusted);
        assertEquals("size", 1, replicatedLogImpl.size());
        assertEquals("dataSize", 1, replicatedLogImpl.dataSize());

        assertEquals("removeFrom - adjusted", -1, replicatedLogImpl.removeFrom(0));
        assertEquals("removeFrom - adjusted", -1, replicatedLogImpl.removeFrom(100));
    }

    @Test
    public void testGetFromWithMax(){
        List<ReplicatedLogEntry> from = replicatedLogImpl.getFrom(0, 1);
        Assert.assertEquals(1, from.size());
        Assert.assertEquals(1, from.get(0).getTerm());

        from = replicatedLogImpl.getFrom(0, 20);
        Assert.assertEquals(4, from.size());
        Assert.assertEquals(2, from.get(3).getTerm());

        from = replicatedLogImpl.getFrom(1, 2);
        Assert.assertEquals(2, from.size());
        Assert.assertEquals(1, from.get(1).getTerm());

    }

    @Test
    public void testSnapshotPreCommit() {
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 4, new MockPayload("E")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 5, new MockPayload("F")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(3, 6, new MockPayload("G")));
        replicatedLogImpl.append(new MockReplicatedLogEntry(3, 7, new MockPayload("H")));

        //sending negative values should not cause any changes
        replicatedLogImpl.snapshotPreCommit(-1, -1);
        assertEquals(8, replicatedLogImpl.size());
        assertEquals(-1, replicatedLogImpl.getSnapshotIndex());

        replicatedLogImpl.snapshotPreCommit(4, 3);
        assertEquals(3, replicatedLogImpl.size());
        assertEquals(4, replicatedLogImpl.getSnapshotIndex());

        replicatedLogImpl.snapshotPreCommit(6, 3);
        assertEquals(1, replicatedLogImpl.size());
        assertEquals(6, replicatedLogImpl.getSnapshotIndex());

        replicatedLogImpl.snapshotPreCommit(7, 3);
        assertEquals(0, replicatedLogImpl.size());
        assertEquals(7, replicatedLogImpl.getSnapshotIndex());

        //running it again on an empty list should not throw exception
        replicatedLogImpl.snapshotPreCommit(7, 3);
        assertEquals(0, replicatedLogImpl.size());
        assertEquals(7, replicatedLogImpl.getSnapshotIndex());


    }

    @Test
    public void testIsPresent() {
        assertTrue(replicatedLogImpl.isPresent(0));
        assertTrue(replicatedLogImpl.isPresent(1));
        assertTrue(replicatedLogImpl.isPresent(2));
        assertTrue(replicatedLogImpl.isPresent(3));

        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 4, new MockPayload("D")));
        replicatedLogImpl.snapshotPreCommit(3, 2); //snapshot on 3
        replicatedLogImpl.snapshotCommit();

        assertFalse(replicatedLogImpl.isPresent(0));
        assertFalse(replicatedLogImpl.isPresent(1));
        assertFalse(replicatedLogImpl.isPresent(2));
        assertFalse(replicatedLogImpl.isPresent(3));
        assertTrue(replicatedLogImpl.isPresent(4));

        replicatedLogImpl.snapshotPreCommit(4, 2); //snapshot on 4
        replicatedLogImpl.snapshotCommit();
        assertFalse(replicatedLogImpl.isPresent(4));

        replicatedLogImpl.append(new MockReplicatedLogEntry(2, 5, new MockPayload("D")));
        assertTrue(replicatedLogImpl.isPresent(5));
    }

    // create a snapshot for test
    public Map takeSnapshot(int numEntries) {
        Map map = new HashMap(numEntries);

        long lastIndex = 0;
        long lastTerm = 0;
        for(int i = 0; i < numEntries; i++) {
            ReplicatedLogEntry entry = replicatedLogImpl.getAtPhysicalIndex(i);
            map.put(entry.getIndex(), entry.getData().toString());
            lastIndex = entry.getIndex();
            lastTerm = entry.getTerm();
        }

        replicatedLogImpl.snapshotPreCommit(lastIndex, lastTerm);
        replicatedLogImpl.snapshotCommit();

        return map;

    }
    class MockAbstractReplicatedLogImpl extends AbstractReplicatedLogImpl {
        @Override
        public void appendAndPersist(ReplicatedLogEntry replicatedLogEntry) {
        }

        @Override
        public void removeFromAndPersist(long index) {
        }

        @Override
        public void setSnapshotIndex(long snapshotIndex) {
            this.snapshotIndex = snapshotIndex;
        }

        @Override
        public void setSnapshotTerm(long snapshotTerm) {
            this.snapshotTerm = snapshotTerm;
        }
    }
}
