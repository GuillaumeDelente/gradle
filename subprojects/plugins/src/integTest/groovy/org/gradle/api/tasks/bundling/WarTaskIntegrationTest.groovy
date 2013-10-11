/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class WarTaskIntegrationTest extends AbstractIntegrationSpec {

    def canCreateAWarArchiveWithNoWebXml() {
        given:
        createDir('content') {
            content1 {
                file 'file1.jsp'
            }
        }
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        createDir('meta-inf') {
            metainf1 {
                file 'file2.txt'
            }
        }
        createDir('classes') {
            org {
                gradle {
                    file 'resource.txt'
                    file 'Person.class'
                }
            }
        }
        createZip("lib.jar") {
            file "Dependency.class"
        }
        and:
        buildFile << """
            task war(type: War) {
                from 'content'
                metaInf {
                    from 'meta-inf'
                }
                webInf {
                    from 'web-inf'
                }
                classpath 'classes'
                classpath 'lib.jar'
                destinationDir = buildDir
                archiveName = 'test.war'
            }
        """

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.isManifestPresentAndFirstEntry()
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('META-INF/metainf1/file2.txt')
        war.assertContainsFile('content1/file1.jsp')
        war.assertContainsFile('WEB-INF/lib/lib.jar')
        war.assertContainsFile('WEB-INF/classes/org/gradle/resource.txt')
        war.assertContainsFile('WEB-INF/classes/org/gradle/Person.class')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')

        war.assertFileContent('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
    }

    def canCreateAWarArchiveWithWebXml() {
        given:
        def webXml = file('some.xml') << '<web/>'
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        and:
        buildFile << """
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webXml = file('some.xml')
                destinationDir = buildDir
                archiveName = 'test.war'
            }
        """

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('WEB-INF/web.xml')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')

        war.assertFileContent('WEB-INF/web.xml', webXml.text)
    }

    def canAddFilesToWebInfDir() {
        given:
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
                file 'ignore.xml'
            }
        }
        createDir('web-inf2') {
            file 'file2.txt'
        }
        and:
        buildFile << """
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webInf {
                    from 'web-inf2'
                    into 'dir2'
                    include '**/file2*'
                }
                destinationDir = buildDir
                archiveName = 'test.war'
            }
        """

        when:
        run 'war'

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')
        war.assertContainsFile('WEB-INF/dir2/file2.txt')
    }

    def "exclude duplicates: webXml precedence over webInf"() {
        given:
        createDir('bad') {
            file('web.xml')
        }
        file('good.xml')

        file('bad/web.xml').text = 'bad'
        file('good.xml').text = 'good'

        buildFile << '''
        task war(type: War) {
            webInf {
                from 'bad'
            }
            webXml = file('good.xml')
            destinationDir = buildDir
            archiveName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/web.xml', 'good')
    }

    def "exclude duplicates: classpath precedence over webInf"() {
        given:
        createDir('bad') {
            lib {
                file('file.txt')
            }
        }
        createDir('good') {
            file('file.txt')
        }

        file('bad/lib/file.txt').text = 'bad'
        file('good/file.txt').text = 'good'

        buildFile << '''
        task war(type: War) {
            webInf {
                from 'bad'
            }
            classpath 'good/file.txt'
            destinationDir = buildDir
            archiveName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/lib/file.txt', 'good')
    }

    def "exclude duplicates: webInf over normal files"() {
        given:
        createDir('bad') {
            file('file.txt')
        }
        createDir('good') {
            file('file.txt')
        }

        file('bad/file.txt').text = 'bad'
        file('good/file.txt').text = 'good'

        buildFile << '''
        task war(type: War) {
            into('WEB-INF') {
                from 'good'
            }
            webInf {
                from 'bad'
            }
            destinationDir = buildDir
            archiveName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/file.txt', 'good')
    }
}
