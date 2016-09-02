/*
 * Copyright © 2015-2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.proto.id;

import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.element.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 * Uniquely identifies an application.
 */
public class ApplicationId extends NamespacedId implements ParentedId<NamespaceId> {
  private final String namespace;
  private final String application;
  private transient Integer hashCode;

  public ApplicationId(String namespace, String application) {
    super(EntityType.APPLICATION);
    this.namespace = namespace;
    this.application = application;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getApplication() {
    return application;
  }

  @Override
  public String getEntityName() {
    return getApplication();
  }

  @Override
  public NamespaceId getParent() {
    return new NamespaceId(namespace);
  }

  public ProgramId program(ProgramType type, String program) {
    return new ProgramId(namespace, application, type, program);
  }

  public ProgramId flow(String program) {
    return new ProgramId(namespace, application, ProgramType.FLOW, program);
  }

  public ProgramId workflow(String program) {
    return new ProgramId(namespace, application, ProgramType.WORKFLOW, program);
  }

  public ProgramId mr(String program) {
    return new ProgramId(namespace, application, ProgramType.MAPREDUCE, program);
  }

  public ProgramId spark(String program) {
    return new ProgramId(namespace, application, ProgramType.SPARK, program);
  }

  public ProgramId worker(String program) {
    return new ProgramId(namespace, application, ProgramType.WORKER, program);
  }

  public ProgramId service(String program) {
    return new ProgramId(namespace, application, ProgramType.SERVICE, program);
  }

  @Override
  public Id.Application toId() {
    return Id.Application.from(namespace, application);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ApplicationId that = (ApplicationId) o;
    return Objects.equals(namespace, that.namespace) &&
      Objects.equals(application, that.application);
  }

  @Override
  public int hashCode() {
    Integer hashCode = this.hashCode;
    if (hashCode == null) {
      this.hashCode = hashCode = Objects.hash(super.hashCode(), namespace, application);
    }
    return hashCode;
  }

  @SuppressWarnings("unused")
  public static ApplicationId fromIdParts(Iterable<String> idString) {
    Iterator<String> iterator = idString.iterator();
    return new ApplicationId(next(iterator, "namespace"), nextAndEnd(iterator, "application"));
  }

  @Override
  protected Iterable<String> toIdParts() {
    return Collections.unmodifiableList(Arrays.asList(namespace, application));
  }

  public static ApplicationId fromString(String string) {
    return EntityId.fromString(string, ApplicationId.class);
  }
}
