'use strict';

var webpack = require('webpack');
var _ = require('lodash');

module.exports = _.merge(
  require('./scalajs.webpack.config'),

  {
    entry: {
      "react" : [
        "/andreas/projects/blended/blended.mgmt.app/target/scala-2.12/scalajs-bundler/main/node_modules/react/umd/react.production.min.js",
        "/andreas/projects/blended/blended.mgmt.app/target/scala-2.12/scalajs-bundler/main/node_modules/react-dom/umd/react-dom.production.min.js"
      ]
    },
    plugins: [
      new webpack.NoEmitOnErrorsPlugin(),
    ],

    module: {
      rules: [
        {
          test: /\.css$/,
          loader: ['style-loader', 'css-loader']
        }, {
          test: /\.(png|jpg|gif|svg|eot|ttf|woff|woff2)$/,
          loader: 'url-loader',
          options: {
            limit: 20000
          }
        }
      ]
    }
  }
);
