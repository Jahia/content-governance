# Blocking publication: analysis and design

The Compliance section reports; it does not stop a page with issues from going live. This page records
what Jahia 8.2 offers to block a publication, checked in the platform source, and the design to follow
when the block is wanted. Nothing here is implemented.

## What the platform offers

| Hook | Can it refuse a publication? | Source |
|---|---|---|
| `PublicationEventListener` | No: `onPublicationCompleted` runs after the content is live | `JCRPublicationService.notifyListeners` |
| Node validators (`JCRNodeValidator`) | No: publication sets `setSkipValidation(true)` on both sessions. They run on save, so they could refuse *saving* a term, which also blocks drafts | `JCRPublicationService.java`, `JCRSessionWrapper.validateNodes` |
| Publication workflow | Yes: a step of the workflow decides whether the content goes live | jContent publish actions call `authoringApi.openPublicationWorkflow` (`publishAction.jsx`, `startWorkflowAction.jsx`) |
| GraphQL `publish` mutation | Skips workflows: it publishes directly (`ComplexPublicationService`, then a `PublicationJob`) for users allowed to publish without a workflow | graphql-dxm-provider `PublicationJCRNodeMutationExtension` |
| A listener on the live workspace | Only after the fact (unpublish): the content was live and cached in between | |

## Design

1. **A compliance publication workflow shipped by the module.** A jBPM process (`.bpmn2`) of type
   `publish`, registered with a `WorklowTypeRegistration` and loaded by `JBPMModuleProcessLoader`, set
   as the site's publication workflow. Its first step calls `ComplianceScanService`
   (`org.jahia.community.contentgovernance.compliance.service.spi`, exported by the bundle) for each
   page of the publication, in each published language:
   - no issue: publish, as the one-step workflow does;
   - issues: either refuse with the list of issues, or create a review task for a Legal group that
     approves (publish anyway) or rejects. To be decided with the site owner.
2. **Close the bypass with roles, not code.** Editors request publication; the `publish` permission
   (publish without workflow, which the GraphQL mutation uses) stays with the approvers.
3. **Scan what the publication sends.** The page tree and the content published with it, not the
   header and footer (they go live with the home page and are checked when it is published). The
   service needs an option to skip ancestor areas and frame paths.
4. **Follow the site switch.** The step checks `SiteFeatures.isEnabled(<site path>, "compliance")`,
   as the GraphQL fields do, so a site that switched the Compliance section off in **Site Settings >
   Content governance** publishes without the check. Whether the workflow should instead refuse to be
   set on such a site is to be decided.
5. **Optional, for comfort:** the Compliance section, or a check run before the workflow dialog
   opens, warns the editor first. It protects nothing on its own.

## Not verified yet

- How a module registers a workflow on 8.2.3.2 (the registration and loader classes are core Spring
  beans; the module-side wiring has not been tried).
- The API of a workflow step that runs Java code and ends the process.

Prototype both on a test site before committing to an estimate.
