import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.common.app.GlobalComponentLookupHelper;
import org.sonatype.nexus.repository.maintenance.MaintenanceService;
import org.sonatype.nexus.repository.storage.ComponentMaintenance;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.script.plugin.RepositoryApi;
import org.sonatype.nexus.script.plugin.internal.provisioning.RepositoryApiImpl;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

def retentionDays = 15;
def retentionBorder = 15;
def repositoryName = 'BartsDockerRepo';
def whitelisted_tag_suffixes = ["ocata", "newton", "latest", "40", "94", "122", "129", "161", "214", "309", "360"].toArray();
def retentionCounter = 0;
log.info(":::Cleanup script started!");
MaintenanceService service = container.lookup("org.sonatype.nexus.repository.maintenance.MaintenanceService");
def repo = repository.repositoryManager.get(repositoryName);
def tx = repo.facet(StorageFacet.class).txSupplier().get();
def components = null;
try {
    tx.begin();
    components = tx.browseComponents(tx.findBucket(repo));
} catch (Exception e) {
    log.info("Error: " + e);
} finally {
    if (tx != null)
        tx.close();
}

if (components != null) {
    def retentionDate = DateTime.now().minusDays(retentionDays).dayOfMonth().roundFloorCopy();
    int deletedComponentCount = 0;
    int compCount = 0;
    def listOfComponents = ImmutableList.copyOf(components);
    def previousComp = listOfComponents.head().name();
    def checkValue = null;
    retentionCounter = 0;
    def prev = null;
    def splCheck = null;
    listOfComponents.reverseEach {  comp ->

        def splited = comp.version();
        def spl = splited.split("-");

        for (i = spl.length - 1; i < spl.length; i++) {
            checkValue = null;
            for (j = 0; j < whitelisted_tag_suffixes.length; j++) {
                Pattern pattern = Pattern.compile(whitelisted_tag_suffixes[j]);
                Matcher matcher = pattern.matcher(spl[i]);
                boolean found = matcher.matches();
                if (found == true) {
                    log.info("true " + j);
                    checkValue = true;
                    log.info("Component skipped: ${comp.name()} ${comp.version()}");
                    return checkValue;
                }
            }
            log.info("else check " + prev + " " + spl[i - 1]);
            if(spl.length < 3){
                splCheck = spl[i - 1];
            } else {
                splCheck = spl[i - 2] + "-" + spl[i - 1];
            }
            if (splCheck == prev) {
                retentionCounter++;
                log.info(retentionCounter + " ", "retention monitor")
                checkValue = false;
                log.info("false " + comp.version());
            } else {
                prev = splCheck;
                retentionCounter = 1;
            }
        }

        if (checkValue == false) {
            if (retentionCounter > retentionBorder) {
                log.info("CompDate: ${comp.lastUpdated()} RetDate: ${retentionDate}");
                if (comp.lastUpdated() > retentionDate) {
                    log.info("compDate after retentionDate: ${comp.lastUpdated()} isAfter ${retentionDate}");
                    log.info("deleting ${comp.name()}, version: ${comp.version()}");
                    // ------------------------------------------------
                    // uncomment to delete components and their assets
                    // service.deleteComponent(repo, comp);
                    // ------------------------------------------------
                    log.info("component deleted");
                    log.info("----------");
                    deletedComponentCount++;
                } else {
                    log.info("retention count " + retentionCounter.toString());
                }
            }
        } else {
            log.info("Component skipped: ${comp.name()} ${comp.version()}");
        }
    }
    log.info("----------");
    log.info("Deleted Component count: ${deletedComponentCount}");
    log.info("----------");
}