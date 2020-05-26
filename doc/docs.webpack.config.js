// node_modules/webpack-cli/bin/cli.js --output-path /home/andreas/projects/blended/core/out/doc --config doc/docs.webpack.config.js

var path = require("path");
const MiniCssExtractPlugin = require("mini-css-extract-plugin");

// out pwd is `target/scala_2.12/scalajs-bundler/node_modules`
// rootDir should be the project base dir
var rootDir = path.resolve(__dirname, "/home/andreas/projects/blended/core/doc");

module.exports = {
  entry: {
    'blended-bootstrap' : [ path.resolve(rootDir, 'scss/bootstrap/blended.scss') ]
  },
  module: {
    rules: [{
      test: /\.scss$/,
      use: [
        {
          loader: MiniCssExtractPlugin.loader
        },
        {
          // Interprets CSS
          loader: "css-loader",
          options: {
            importLoaders: 2
          }
        },
        {
          loader: 'sass-loader'
        }
      ]
    }]
  },
  plugins: [
    // Where the compiled SASS is saved to
    new MiniCssExtractPlugin({
      filename: 'blended-bootstrap.css',
      allChunks: true,
    })
  ],
};
