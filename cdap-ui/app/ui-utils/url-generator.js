/*
 * Copyright © 2016 Cask Data, Inc.
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

/*
  Purpose: To generate absolute URLs to navigate between CDAP and Extensions (Hydrator & Tracker)
    as they are separate web apps with independent UI routing
  I/P: Context/Naviation object that has,
    - uiApp - cdap, hydrator or tracker
    - namespaceId, appId, entityType, entityId & runId to generate the complete URL if
      appropriate context is available.

  O/P: Absolute Url of the form:
    <protocol>//<host>/cask-:uiApp/:namespaceId/apps/:appId/:entityType/:entityId/runs/:runId

  The absolute URL will be generated based on the available context.

  Note:
    This is attached to the window object as this needs to be used in both CDAP (react app) and
    in hydrator & tracker (angular apps). For now it is attached to window as its a pure function
    without any side effects. Moving forward once we have everything in react we should use this
    as a proper utility function in es6 module system.
*/
window.getAbsUIUrl = function(navigationObj = {}) {
  let {uiApp = 'cask-cdap', redirectUrl, clientId, namespaceId, appId, entityType, entityId, runId} = navigationObj;
  let baseUrl = `${location.protocol}//${location.host}/${uiApp}`;
  if (uiApp === 'login') {
    baseUrl += `?`;
  }
  if (redirectUrl) {
    baseUrl += `redirectUrl=${encodeURIComponent(redirectUrl)}`;
  }
  if (clientId) {
    baseUrl += `&clientId=${clientId}`;
  }
  if (namespaceId) {
    baseUrl += `/ns/${namespaceId}`;
  }
  if (appId) {
    baseUrl += `/apps/${appId}`;
  }
  if (entityType && entityId) {
    baseUrl += `/${entityType}/${entityId}`;
  }
  if (runId) {
    baseUrl += `/runs/${runId}`;
  }
  return baseUrl;
};
