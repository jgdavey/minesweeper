require "rake"
require "rake/clean"
require "digest/md5"
require "json"

# Output settings
DEV_OUTPUT_DIR = "resources/public"
DIST_OUTPUT_DIR = "dist"
CSS_PATH = "css"
JS_PATH = "js"
IMG_PATH = "images"

CLEAN.include(DIST_OUTPUT_DIR)

task :default => :test

# CSS settings
SCSS_INPUT_DIR = "src/scss"
DIST_CSS = "#{DIST_OUTPUT_DIR}/#{CSS_PATH}"
SCSS_FILES = Rake::FileList["#{SCSS_INPUT_DIR}/**/*.scss"] do |files|
  files.exclude("~*")
end
CSS_OUT_FILES = SCSS_FILES.pathmap("%{^#{SCSS_INPUT_DIR}/,#{DIST_CSS}/}X.css")

namespace :css do
  desc "Dev-time watch and build CSS files"
  task :watch do
    out = "#{DEV_OUTPUT_DIR}/#{CSS_PATH}"
    args = %w[npx node-sass --watch --recursive --source-map true --output] + [out, SCSS_INPUT_DIR]
    sh(*args)
  end

  CLOBBER.include Dir["#{DEV_OUTPUT_DIR}/#{CSS_PATH}/*.css"]

  def css_source(css)
    css.pathmap("%{^#{DIST_CSS}/,#{SCSS_INPUT_DIR}/}X.scss")
  end

  rule ".css" => [->(f) {css_source(f)}, DIST_CSS] do |t|
    dirname = t.name.pathmap("%d")
    if dirname != DIST_CSS
      mkdir_p dirname
    end

    sh "npx node-sass --output-style compressed #{t.source} > #{t.name}"
  end

  directory DIST_CSS

  desc "Build all CSS files"
  task :build => CSS_OUT_FILES
end

namespace :cljs do
  CLOBBER.include Dir["#{DEV_OUTPUT_DIR}/#{JS_PATH}*/*.js"]

  task :build do
    sh "npx shadow-cljs release main"
  end
end

class Hasher
  def initialize(paths)
    @paths = paths
  end

  def entries
    @paths.map do |path|
      hash = Digest::MD5.hexdigest(File.read(path))
      hashed = path.pathmap("%X.#{hash}%x")
      [path, hashed]
    end
  end
end

class Replacer
  def initialize(path)
    @path = path
    @contents = File.read(path)
  end

  def substitute!(from, to)
    @contents.gsub!(from, to)
  end

  def write!
    File.write(@path, @contents)
  end
end

namespace :dist do
  multitask :build => ["css:build", "cljs:build", :html, :images]

  IMAGES_DIR = "#{DIST_OUTPUT_DIR}/#{IMG_PATH}"
  directory IMAGES_DIR

  SOURCE_IMAGES = Rake::FileList["#{DEV_OUTPUT_DIR}/#{IMG_PATH}/**/*"]
  DIST_IMAGES = SOURCE_IMAGES.pathmap("%{#{DEV_OUTPUT_DIR},#{DIST_OUTPUT_DIR}}p")
  SOURCE_IMAGES.zip(DIST_IMAGES).each do |(src, dest)|
    file dest => [src, IMAGES_DIR] do |t|
      cp_r t.prerequisites.first, t.name
    end
  end

  task :images => DIST_IMAGES

  MANIFEST_FILE = "#{DIST_OUTPUT_DIR}/manifest.json"

  desc "Build the whole thing to #{DIST_OUTPUT_DIR}, with hashed assets"
  task :assets => :build do
    Dir["#{DIST_OUTPUT_DIR}/#{JS_PATH}/**/*.*.js"].each do |old_js|
      rm old_js
    end

    image_entries = Hasher.new(DIST_IMAGES).entries
    # update css images
    CSS_OUT_FILES.each do |path|
      replacer = Replacer.new(path)
      image_entries.each do |(from, to)|
        replacer.substitute!(from.pathmap("%{#{DIST_OUTPUT_DIR},}p"),
                             to.pathmap("%{#{DIST_OUTPUT_DIR},}p"))
      end
      replacer.write!
    end

    # hash css and js
    css_entries = Hasher.new(CSS_OUT_FILES).entries
    js_entries = Hasher.new(Dir["#{DIST_OUTPUT_DIR}/#{JS_PATH}/**/*.js"]).entries
    all = (image_entries + js_entries + css_entries).map do |(src, dest)|
      cp src, dest
      [src.pathmap("%{#{DIST_OUTPUT_DIR},}p"),
       dest.pathmap("%{#{DIST_OUTPUT_DIR},}p")]
    end
    manifest_contents = JSON.pretty_generate(Hash[all])
    File.write(MANIFEST_FILE, manifest_contents)

    # update html js/css
    HTML_DEST.each do |path|
      replacer = Replacer.new(path)
      all.each do |(from, to)|
        replacer.substitute!(from, to)
      end
      replacer.write!
    end
  end

  HTML_SRC = Rake::FileList["#{DEV_OUTPUT_DIR}/**/*.html"]
  HTML_DEST = HTML_SRC.pathmap("%{#{DEV_OUTPUT_DIR},#{DIST_OUTPUT_DIR}}p")
  HTML_SRC.zip(HTML_DEST).each do |(src, dest)|
    file dest => src do |t|
      cp_r t.prerequisites.first, t.name
    end
  end

  task :html => HTML_DEST
end


task :package => "dist:assets" do
end

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
