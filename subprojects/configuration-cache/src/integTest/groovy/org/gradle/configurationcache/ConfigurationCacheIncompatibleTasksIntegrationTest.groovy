/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheIncompatibleTasksIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "reports incompatible task serialization and execution problems and discards cache entry when task is scheduled"() {
        addTasksWithProblems()
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        result.assertHasPostBuildOutput("Configuration cache entry discarded with 1 problem.")

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        result.assertHasPostBuildOutput("Configuration cache entry discarded with 1 problem.")
    }

    def "problems in tasks that are not marked incompatible are treated as failures when incompatible tasks are also scheduled"() {
        addTasksWithProblems()
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheFails("declared", "notDeclared")

        then:
        result.assertTasksExecuted(":declared", ":notDeclared")
        configurationCache.assertStateStored()
        problems.assertFailureHasProblems(failure) {
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:notDeclared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        outputContains("Configuration cache entry discarded with 2 problems.")

        when:
        configurationCacheFails("declared", "notDeclared")

        then:
        result.assertTasksExecuted(":declared", ":notDeclared")
        configurationCache.assertStateStored()
        problems.assertFailureHasProblems(failure) {
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:notDeclared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        outputContains("Configuration cache entry discarded with 2 problems.")
    }

    private addTasksWithProblems() {
        buildFile("""
            class Broken extends DefaultTask {
                private final configurations = project.configurations

                @TaskAction
                void execute() {
//                    project.configurations
                }
            }

            tasks.register("declared", Broken) {
                notCompatibleWithConfigurationCache("retains configuration container")
            }

            tasks.register("notDeclared", Broken) {
            }

            tasks.register("ok") {
                doLast { }
            }
        """)
    }
}
