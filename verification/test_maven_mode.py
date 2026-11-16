"""Exercise the real Gradle resolver and local publisher using isolated Maven fixtures."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


BUILD = r'''
plugins { id 'maven-publish' }
group = 'maven.mode.fixture'
version = '1.0'
def originalRepositories = repositories.collect { it.name }
def scriptRepositories = buildscript.repositories.collect { it.name }
repositories.removeAll { it.name != 'MavenLocal' }
repositories.maven { name = 'FixtureRemote'; url = uri('@REMOTE@') }
configurations { probe; fallback }
dependencies {
    probe 'maven.mode.fixture:input:1.0'
    fallback 'maven.mode.fixture:fallback:1.0'
}
tasks.register('fixtureJar', Jar) {
    archiveFileName = project.name + '-fixture.jar'
    destinationDirectory = layout.buildDirectory.dir('libs')
}
publishing.publications {
    fixture(MavenPublication) { artifact tasks.named('fixtureJar') }
}
tasks.register('verifyMavenMode') {
    doLast {
        def marker = { configuration ->
            new java.util.zip.ZipFile(configuration.singleFile).withCloseable { archive ->
                archive.getInputStream(archive.getEntry('marker.txt')).getText('UTF-8')
            }
        }
        def result = [repositories: originalRepositories,
                      buildscriptRepositories: scriptRepositories,
                      pluginRepositories: gradle.ext.fixturePluginRepositories,
                      settingsRepositories: gradle.ext.fixtureSettingsRepositories,
                      localRepository: System.getProperty('maven.repo.local'),
                      selected: marker(configurations.probe),
                      fallback: marker(configurations.fallback)]
        def output = layout.buildDirectory.file('maven-mode.json').get().asFile
        output.parentFile.mkdirs()
        output.text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result))
    }
}
'''

INIT = '''
gradle.settingsEvaluated { settings ->
    gradle.ext.fixturePluginRepositories = settings.pluginManagement.repositories.collect { it.name }
    gradle.ext.fixtureSettingsRepositories = settings.dependencyResolutionManagement.repositories.collect { it.name }
}
'''


def artifact(repository, name, marker):
    base = repository / 'maven/mode/fixture' / name / '1.0'
    base.mkdir(parents=True)
    (base / f'{name}-1.0.pom').write_text(
        '<project><modelVersion>4.0.0</modelVersion><groupId>maven.mode.fixture</groupId>'
        f'<artifactId>{name}</artifactId><version>1.0</version></project>\n'
    )
    with zipfile.ZipFile(base / f'{name}-1.0.jar', 'w') as archive:
        archive.writestr('marker.txt', marker)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle', required=True, type=Path)
    parser.add_argument('--scratch', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    scratch = args.scratch.resolve()
    scratch.mkdir(parents=True, exist_ok=False)
    local = scratch / 'local-repository'
    remote = scratch / 'remote-repository'
    artifact(local, 'input', 'local')
    artifact(remote, 'input', 'remote')
    artifact(remote, 'fallback', 'remote-fallback')
    results = {}
    cases = [('local', 'true', str(local)), ('remote', 'false', str(local)),
             ('default-path', 'true', ''), ('invalid-mode', 'invalid', str(local)),
             ('relative-path', 'true', 'relative/repository')]
    for name, enabled, repository in cases:
        project = scratch / name
        project.mkdir()
        shutil.copyfile(root / 'settings.gradle.kts', project / 'settings.gradle.kts')
        with (project / 'settings.gradle.kts').open('a') as settings:
            settings.write('\ninclude(":child")\n')
        (project / 'gradle.properties').write_text('project.name=maven-mode-fixture\norg.gradle.jvmargs=-Xmx512m\n')
        (project / 'maven.properties').write_text(f'maven.local={enabled}\nmaven.localRepository={repository}\n')
        (project / 'inspection.init.gradle').write_text(INIT)
        for directory in [project, project / 'child']:
            directory.mkdir(exist_ok=True)
            (directory / 'build.gradle').write_text(BUILD.replace('@REMOTE@', remote.as_uri()))
        invalid = name in ('invalid-mode', 'relative-path')
        tasks = ['help'] if invalid else ['verifyMavenMode', ':child:verifyMavenMode']
        if name == 'local':
            tasks.append('publishToMavenLocal')
        command = [str(args.gradle.resolve()), *tasks, '--offline', '--no-daemon',
                   '--no-configuration-cache', '--console=plain', '--max-workers=2',
                   '-I', str(project / 'inspection.init.gradle')]
        if name == 'remote':
            command += ['-Pproject.repositories.mavenLocal=true',
                        '-Dmaven.repo.local=' + str(scratch / 'ignored-cli-path')]
        with (project / 'gradle.log').open('w') as log:
            completed = subprocess.run(command, cwd=project, stdout=log, stderr=subprocess.STDOUT)
        output = (project / 'gradle.log').read_text()
        if invalid:
            expected = ('maven.properties must set maven.local=true or false' if name == 'invalid-mode'
                        else 'maven.localRepository must be an absolute path')
            assert completed.returncode != 0 and expected in output, output
        else:
            assert completed.returncode == 0, output
            for directory in [project, project / 'child']:
                result = json.loads((directory / 'build/maven-mode.json').read_text())
                for key in ['repositories', 'buildscriptRepositories', 'pluginRepositories', 'settingsRepositories']:
                    if enabled == 'true':
                        assert result[key][0] == 'MavenLocal', result
                    else:
                        assert 'MavenLocal' not in result[key], result
                expected_path = str(local) if repository else str(Path.home() / '.m2/repository')
                assert result['localRepository'] == expected_path, result
                assert result['selected'] == ('local' if name == 'local' else 'remote'), result
                assert result['fallback'] == 'remote-fallback', result
            if name == 'local':
                for module in ['maven-mode-fixture', 'child']:
                    published = local / 'maven/mode/fixture' / module / '1.0'
                    assert (published / f'{module}-1.0.jar').is_file(), published
                    assert (published / f'{module}-1.0.pom').is_file(), published
        results[name] = {'passed': True, 'gradleExit': completed.returncode}
        print(f'{name}: passed', flush=True)
    (scratch / 'results.json').write_text(json.dumps(results, indent=2) + '\n')


if __name__ == '__main__':
    main()
