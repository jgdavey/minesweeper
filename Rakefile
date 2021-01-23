require "rake"
require "rake/clean"

JS_OUTPUT_DIR = "resources/public/js"
CSS_OUTPUT_DIR = "resources/public/css"
OUTPUT_JAR = "target/zebra.jar"

task :default => :test

namespace :css do
  SCSS_INPUT_DIR = "src/scss"

  SCSS_FILES = Rake::FileList["#{SCSS_INPUT_DIR}/**/*.scss"] do |files|
    files.exclude("~*")
  end

  CSS_OUT_FILES = SCSS_FILES.pathmap("%{^#{SCSS_INPUT_DIR}/,#{CSS_OUTPUT_DIR}/}X.css")
  CLEAN.include(CSS_OUT_FILES)

  def css_source(css)
    css.pathmap("%{^#{CSS_OUTPUT_DIR}/,#{SCSS_INPUT_DIR}/}X.scss")
  end

  rule ".css" => [->(f) {css_source(f)}, CSS_OUTPUT_DIR] do |t|
    dirname = t.name.pathmap("%d")
    if dirname != CSS_OUTPUT_DIR
      mkdir_p dirname
    end

    sh "npx node-sass --output-style compressed #{t.source} > #{t.name}"
  end

  directory CSS_OUTPUT_DIR

  desc "Build all CSS files"
  task :build => CSS_OUT_FILES

  desc "Dev-time watch and build CSS files"
  task :watch do
    args = %w[npx node-sass --watch --recursive --source-map true --output] + [CSS_OUTPUT_DIR, SCSS_INPUT_DIR]
    sh(*args)
  end
end

namespace :cljs do
  CLEAN.include Dir["#{JS_OUTPUT_DIR}/*/*.js"]

  task :clean do
    Dir["#{JS_OUTPUT_DIR}/*/*.js"].tap do |files|
      rm(files) if files.any?
    end
  end

  task :build => :clean do
    sh "npx shadow-cljs release main"
  end
end

namespace :clj do
  CLEAN << "classes"
  directory "classes"

  task :build => "classes" do
    sh <<~SH
      clojure -M -e "(do (compile 'zebra.main) :compiled)"
    SH
  end
end

file OUTPUT_JAR => :build do |f|
  build_script = <<~CLJ
  (do
    (require '[uberdeps.api :as uberdeps])

    (let [exclusions (into uberdeps/exclusions [#"\.DS_Store"
                                                #".*\.cljs"
                                                #".*/cljs-runtime/.*"
                                                #"(?i)^META-INF/INDEX.LIST$"
                                                #"(?i)^META-INF/[^/]*\.(MF|SF|RSA|DSA)$"
                                                #"(?i)^META-INF/LICENSE$"
                                                #"(?i)^LICENSE$"])
          deps       (clojure.edn/read-string (slurp "deps.edn"))]
      (binding [uberdeps/exclusions exclusions
                uberdeps/level      :warn]
        (uberdeps/package deps "#{f.name}" {:aliases \#{:package}
                                            :main-class "zebra.main"}))))

  CLJ
  sh "clojure",
    "-Sdeps",
    '{:aliases {:uberjar {:replace-deps {uberdeps/uberdeps {:mvn/version "1.0.4"}} :replace-paths []}}}',
    "-M:uberjar",
    "-e",
    build_script
end

CLEAN << OUTPUT_JAR

multitask :build => ["css:build", "cljs:build", "clj:build"]

desc "Build the whole thing to a jar"
task :package => OUTPUT_JAR

namespace :test do
  task :prepare do
    rm_rf "target/ci.js"
    sh "npm install"
  end

  desc "Run cljs tests via karma"
  task :cljs => :prepare do
    sh "npx shadow-cljs compile ci-tests"
    sh "npx karma start --single-run"
  end

  desc "Run clj tests"
  task :clj do
    sh "clj -M:dev:clj-tests"
  end

  desc "Run all tests"
  task :all => [:cljs, :clj]
end

task :test => "test:all"
