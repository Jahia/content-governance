// Removes the luxe quality dataset of content-governance, in the edit and the live workspaces: the
// pages under /sites/luxe/home/quality-lab and the terms of /sites/luxe/contents/quality-lab-terms.
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRTemplate

def SITE = 'luxe'
def paths = ['/sites/' + SITE + '/home/quality-lab', '/sites/' + SITE + '/contents/quality-lab-terms']
def report = []
[Constants.LIVE_WORKSPACE, Constants.EDIT_WORKSPACE].each { ws ->
    JCRTemplate.getInstance().doExecuteWithSystemSession(null, ws, Locale.ENGLISH, { session ->
        paths.each { p ->
            if (session.nodeExists(p)) {
                session.getNode(p).remove()
                report << 'removed ' + p + ' in ' + ws
            }
        }
        session.save()
        return null
    } as JCRCallback)
}
log.info('luxe quality dataset: {}', report ? report.join('; ') : 'nothing to remove')
return report ? report.join('\n') : 'nothing to remove'
