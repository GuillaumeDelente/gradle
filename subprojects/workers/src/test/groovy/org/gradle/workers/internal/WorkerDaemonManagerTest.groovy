/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.workers.internal

import org.gradle.internal.operations.BuildOperationWorkerRegistry
import org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.process.internal.health.memory.MemoryManager
import spock.lang.Specification
import spock.lang.Subject

class WorkerDaemonManagerTest extends Specification {

    def clientsManager = Mock(WorkerDaemonClientsManager)
    def client = Mock(WorkerDaemonClient)
    def memoryManager = Mock(MemoryManager)
    def buildOperationWorkerRegistry = Mock(BuildOperationWorkerRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def workerOperation = Mock(Operation)
    def buildOperation = Mock(BuildOperationExecutor.Operation)

    @Subject manager = new WorkerDaemonManager(clientsManager, memoryManager, buildOperationWorkerRegistry, buildOperationExecutor)

    def workingDir = new File("some-dir")
    def worker = Stub(WorkerDaemonAction)
    def options = Stub(DaemonForkOptions)
    def spec = Stub(WorkSpec)
    def serverImpl = Stub(WorkerDaemonProtocol)

    def "getting a worker daemon does not assume client use"() {
        when:
        manager.getDaemon(serverImpl.class, workingDir, options);

        then:
        0 * clientsManager._
    }

    def "new client is created when daemon is executed and no idle clients found"() {
        when:
        manager.getDaemon(serverImpl.class, workingDir, options).execute(worker, spec)

        then:
        1 * buildOperationWorkerRegistry.getCurrent() >> workerOperation
        1 * buildOperationExecutor.getCurrentOperation() >> buildOperation

        then:
        1 * clientsManager.reserveIdleClient(options) >> null

        then:
        1 * clientsManager.reserveNewClient(serverImpl.class, workingDir, options) >> client

        then:
        1 * client.execute(worker, spec, workerOperation, buildOperation)

        then:
        1 * clientsManager.release(client)
        0 * _._
    }

    def "idle client is reused when daemon is executed"() {
        when:
        manager.getDaemon(serverImpl.class, workingDir, options).execute(worker, spec)

        then:
        1 * buildOperationWorkerRegistry.getCurrent() >> workerOperation
        1 * buildOperationExecutor.getCurrentOperation() >> buildOperation

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * client.execute(worker, spec, workerOperation, buildOperation)

        then:
        1 * clientsManager.release(client)
        0 * _._
    }

    def "client is released even if execution fails"() {
        when:
        manager.getDaemon(serverImpl.class, workingDir, options).execute(worker, spec)

        then:
        1 * buildOperationWorkerRegistry.getCurrent() >> workerOperation
        1 * buildOperationExecutor.getCurrentOperation() >> buildOperation

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * client.execute(worker, spec, workerOperation, buildOperation) >> { throw new RuntimeException("Boo!") }

        then:
        thrown(RuntimeException)
        1 * clientsManager.release(client)
        0 * _._
    }

    def "registers/deregisters a worker daemon expiration with the memory manager"() {
        WorkerDaemonExpiration workerDaemonExpiration

        when:
        def manager = new WorkerDaemonManager(clientsManager, memoryManager, buildOperationWorkerRegistry, buildOperationExecutor)

        then:
        1 * memoryManager.addMemoryHolder(_) >> { args -> workerDaemonExpiration = args[0] }

        when:
        manager.stop()

        then:
        1 * memoryManager.removeMemoryHolder(_) >> { args -> assert args[0] == workerDaemonExpiration }
    }

    def "stops clients"() {
        when:
        manager.stop()

        then:
        clientsManager.stop()
    }
}
