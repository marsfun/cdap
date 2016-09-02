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
 * Uniquely identifies a program run.
 */
public class ProgramRunId extends NamespacedEntityId implements ParentedId<ProgramId> {
  private final String application;
  private final ProgramType type;
  private final String program;
  private final String run;
  private transient Integer hashCode;

  public ProgramRunId(String namespace, String application, ProgramType type, String program, String run) {
    super(EntityType.PROGRAM_RUN, namespace);
    this.application = application;
    this.type = type;
    this.program = program;
    this.run = run;
  }

  public String getApplication() {
    return application;
  }

  public ProgramType getType() {
    return type;
  }

  public String getProgram() {
    return program;
  }

  @Override
  public String getEntityName() {
    return getProgram();
  }

  public String getRun() {
    return run;
  }

  @Override
  public ProgramId getParent() {
    return new ProgramId(namespace, application, type, program);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ProgramRunId that = (ProgramRunId) o;
    return Objects.equals(namespace, that.namespace) &&
      Objects.equals(application, that.application) &&
      Objects.equals(type, that.type) &&
      Objects.equals(program, that.program) &&
      Objects.equals(run, that.run);
  }

  @Override
  public int hashCode() {
    Integer hashCode = this.hashCode;
    if (hashCode == null) {
      this.hashCode = hashCode = Objects.hash(super.hashCode(), namespace, application, type, program, run);
    }
    return hashCode;
  }

  @Override
  public Id.Program.Run toId() {
    return new Id.Program.Run(Id.Program.from(namespace, application, type, program), run);
  }

  @SuppressWarnings("unused")
  public static ProgramRunId fromIdParts(Iterable<String> idString) {
    Iterator<String> iterator = idString.iterator();
    return new ProgramRunId(
      next(iterator, "namespace"), next(iterator, "category"),
      ProgramType.valueOfPrettyName(next(iterator, "type")),
      next(iterator, "program"), nextAndEnd(iterator, "run"));
  }

  @Override
  protected Iterable<String> toIdParts() {
    return Collections.unmodifiableList(
      Arrays.asList(namespace, application, type.getPrettyName().toLowerCase(), program, run)
    );
  }

  public static ProgramRunId fromString(String string) {
    return EntityId.fromString(string, ProgramRunId.class);
  }
}
