/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.project.shared.dto;

import org.eclipse.che.dto.shared.DTO;

/**
 * DTO represents the information about the text change of a file.
 *
 * @author Roman Nikitenko
 */
@DTO
public interface TextChangeDto {

    /** Returns the offset of the change. */
    int getOffset();

    void setOffset(int offset);

    /** Returns length of the text change. */
    int getLength();

    void setLength(int length);


    /** Returns text of the change. */
    String getText();

    void setText(String text);


    /** Returns the ID of the working copy owner */
    String getWorkingCopyOwnerID();

    /** Sets the ID of the working copy owner */
    void setWorkingCopyOwnerID(String id);

    TextChangeDto withWorkingCopyOwnerID(String id);

    /** Returns the path to the project that contains the modified file */
    String getProjectPath();

    /** Sets the path to the project that contains the modified file */
    void setProjectPath(String path);

    TextChangeDto withProjectPath(String path);

    /** Returns the full path to the file that was changed */
    String getFileLocation();

    /** Sets the full path to the file that was changed */
    void setFileLocation(String fileLocation);

    TextChangeDto withFileLocation(String fileLocation);

    /** Returns the number of characters removed from the file. */
    int getRemovedCharCount();

    /** Sets the number of characters removed from the file. */
    void setRemovedCharCount(int removedCharCount);

    TextChangeDto withRemovedCharCount(int removedCharCount);

    TextChangeDto withOffset(int offset);

    TextChangeDto withLength(int length);

    TextChangeDto withText(String text);
}
