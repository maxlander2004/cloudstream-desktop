#!/usr/bin/env perl
#
# Dependency drift between upstream's `library` module and this port's `:cs-compat`.
#
# Upstream declares its library dependencies through a Gradle version catalog. `:cs-compat`
# compiles those same sources but has to repeat every dependency literally, because it builds
# them in its own project. When a release bumps a version, the scheduled upstream sync brings the
# new sources in while the literal in `cs-compat/build.gradle.kts` stays behind — which compiles
# fine and then fails at runtime with a `NoSuchMethodError` inside an extension.
#
# This compares the two lists and exits non-zero naming the drift. The sync workflow runs it
# before it compiles anything, so a bump arrives as a precise message instead of a mystery.
#
# Usage: perl scripts/check-dependency-drift.pl
#
use strict;
use warnings;
use Cwd qw(abs_path);
use File::Basename qw(dirname);
use File::Spec ();

my $root = abs_path(File::Spec->catdir(dirname(abs_path($0)), File::Spec->updir()));
my $toml     = "$root/src-cloudstream/gradle/libs.versions.toml";
my $upstream = "$root/src-cloudstream/library/build.gradle.kts";
my $compat   = "$root/cloudstream-desktop/cs-compat/build.gradle.kts";

for my $file ($toml, $upstream, $compat) {
    die "missing $file\n" unless -f $file;
}

sub slurp {
    my ($path) = @_;
    open my $fh, '<', $path or die "cannot read $path: $!";
    local $/;
    return <$fh>;
}

# --- upstream's catalog -------------------------------------------------------

my (%versions, %libs, %bundles);
my $section = '';
for my $line (split /\n/, slurp($toml)) {
    if ($line =~ /^\s*\[([^\]]+)\]/) {
        $section = $1;
        next;
    }
    if ($section eq 'versions' && $line =~ /^\s*([A-Za-z0-9_.-]+)\s*=\s*(.+)$/) {
        my ($key, $rest) = ($1, $2);
        if    ($rest =~ /strictly\s*=\s*"([^"]+)"/) { $versions{$key} = $1 }
        elsif ($rest =~ /"([^"]+)"/)                 { $versions{$key} = $1 }
    }
    elsif ($section eq 'libraries' && $line =~ /^\s*([A-Za-z0-9_.-]+)\s*=\s*\{\s*module\s*=\s*"([^"]+)"(.*)$/) {
        my ($alias, $module, $rest) = ($1, $2, $3);
        my $version;
        if    ($rest =~ /version\.ref\s*=\s*"([^"]+)"/) { $version = $versions{$1} }
        elsif ($rest =~ /version\s*=\s*"([^"]+)"/)      { $version = $1 }
        $libs{$alias} = { module => $module, version => $version };
    }
    elsif ($section eq 'bundles' && $line =~ /^\s*([A-Za-z0-9_.-]+)\s*=\s*\[(.*)\]/) {
        my @items = $2 =~ /"([^"]+)"/g;
        $bundles{$1} = \@items;
    }
}

# --- what the library actually asks for --------------------------------------

my %wanted;
for my $line (split /\n/, slurp($upstream)) {
    next unless $line =~ /(?:implementation|api|compileOnly)\s*\(\s*libs\.([A-Za-z0-9_.]+)\s*\)/;
    my @aliases;
    if ($1 =~ /^bundles\.(.+)$/) {
        my $bundle = $1;
        $bundle =~ s/\./-/g;
        @aliases = @{ $bundles{$bundle} || [] };
    }
    else {
        my $alias = $1;
        $alias =~ s/\./-/g;
        @aliases = ($alias);
    }

    for my $alias (@aliases) {
        # Test-only coordinates are not part of what cs-compat compiles.
        next if $alias =~ /-test$|[.-]test[.-]|^junit/;

        my $entry = $libs{$alias};
        unless ($entry) {
            warn "note: upstream alias `$alias` is not in the catalog, skipping\n";
            next;
        }
        next if $entry->{module} =~ /-test$|^junit|:junit|kotlin-test/;
        unless (defined $entry->{version}) {
            warn "note: no version for `$alias`, skipping\n";
            next;
        }
        $wanted{"$entry->{module}:$entry->{version}"} = 1;
    }
}

# --- what cs-compat actually declares ----------------------------------------

my %have;
for my $line (split /\n/, slurp($compat)) {
    while ($line =~ /"([A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-]+(?::[^"]+)?)"/g) {
        my ($group, $artifact, $version) = split /:/, $1, 3;
        $have{"$group:$artifact"} = defined $version ? $version : '';
    }
}

# --- compare -----------------------------------------------------------------

my (@drift, @notes);
for my $coord (sort keys %wanted) {
    my ($group, $artifact, $version) = split /:/, $coord, 3;
    my $key = "$group:$artifact";
    if (!exists $have{$key}) {
        push @drift, "MISSING   $key\n            upstream wants $version";
        next;
    }
    # A coordinate declared without a version is resolved by the Kotlin plugin's own alignment
    # (kotlin-reflect), which is the same version upstream's catalog names in practice. Say so
    # rather than failing on a declaration that is intentionally versionless.
    if ($have{$key} eq '') {
        push @notes, "$key is declared without a version here; upstream names $version " .
                     "(the Kotlin plugin supplies it)";
        next;
    }
    if ($have{$key} ne $version) {
        push @drift, "VERSION   $key\n            upstream $version, cs-compat " . $have{$key};
    }
}

if (@drift) {
    print "Dependency drift between upstream's library and :cs-compat\n\n";
    print "$_\n" for @drift;
    print "\nUpdate cloudstream-desktop/cs-compat/build.gradle.kts to match upstream's catalog.\n";
    exit 1;
}

my $count = scalar keys %wanted;
print "No dependency drift: $count upstream librar" . ($count == 1 ? 'y' : 'ies') .
      " dependenc" . ($count == 1 ? 'y' : 'ies') . " match :cs-compat.\n";
print "  note: $_\n" for @notes;
exit 0;
